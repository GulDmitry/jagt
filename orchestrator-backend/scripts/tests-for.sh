#!/usr/bin/env bash
# Prints the Gradle lines that check a change, in the order to run them, each under a timeout; stop at the first red.
# Usage: scripts/tests-for.sh [path...]   (paths relative to the repository root; none = this branch against main)
# Every line runs from the repository root. Pitest is not among them: it runs once, at the done command.
set -eu

root=$(git rev-parse --show-toplevel)
cd "$root"
backend=orchestrator-backend
modules="statechart core usecase adapter surface stand mutation-scope"
other_set_names="LinuxTest BoardTest E2e EvalSupport PromptEval MasterEval TriageEval AcceptanceEval"

if [ "$#" -gt 0 ]; then
  paths=$(printf '%s\n' "$@" | sed "s#^$root/##")
else
  paths=$({
    git diff --name-only --no-renames main...HEAD
    git diff --name-only --no-renames HEAD
    git ls-files --others --exclude-standard
  } | sort -u)
fi

dependents() {
  case "$1" in
    statechart) echo "core usecase adapter surface stand root" ;;
    core) echo "usecase adapter surface stand root" ;;
    usecase) echo "adapter surface stand root" ;;
    adapter) echo "stand root" ;;
    surface) echo "root" ;;
    *) echo "" ;;
  esac
}

capitalize() { printf '%s' "$1" | awk '{print toupper(substr($0, 1, 1)) substr($0, 2)}'; }

prefix() { if [ "$1" = root ]; then echo ""; else echo ":$1"; fi; }

test_dir() { if [ "$1" = root ]; then echo "$backend/src/test/java"; else echo "$backend/$1/src/test/java"; fi; }

# "module set" for a path under a source set, nothing otherwise
locate() {
  case "$1" in
    "$backend"/src/*/*)
      rest=${1#"$backend"/src/}
      echo "root ${rest%%/*}"
      ;;
    "$backend"/*/src/*/*)
      rest=${1#"$backend"/}
      mod=${rest%%/*}
      for m in $modules; do
        if [ "$m" = "$mod" ]; then
          rest=${rest#*/src/}
          echo "$mod ${rest%%/*}"
        fi
      done
      ;;
  esac
}

add_unique() { # list item
  case " $1 " in
    *" $2 "*) printf '%s' "$1" ;;
    *) printf '%s %s' "$1" "$2" ;;
  esac
}

touched_projects=""
other_sets=""
removed_main=0
touched_tests=""
rule_names=""
board_touched=0
fitness_touched=0

add_rules() { for r in "$@"; do rule_names=$(add_unique "$rule_names" "$r"); done; }

add_test() { touched_tests=$(add_unique "$touched_tests" "$1:$2"); }

find_tests() { # module class: "module:Class" of every test naming the class, in the module and its dependents
  dirs=""
  for m in $1 $(dependents "$1"); do dirs="$dirs $(test_dir "$m")"; done
  # shellcheck disable=SC2086
  { git ls-files --cached --others --exclude-standard -- $dirs | grep -E "/$2[A-Za-z0-9]*Test\.java$" || true
    git grep --untracked -lw "$2" -- $dirs | grep -E 'Test\.java$' || true; } | sort -u |
    while read -r f; do
      located=$(locate "$f")
      echo "${located%% *}:$(basename "$f" .java)"
    done
}

for p in $paths; do
  case "$p" in
    *.java)
      add_rules Packages VendorNames Kinds Fitness
      loc=$(locate "$p")
      if [ -n "$loc" ]; then
        mod=${loc%% *}
        set=${loc#* }
        touched_projects=$(add_unique "$touched_projects" "$mod")
        # a whole-context HTTP test exercises the wiring without naming the class
        if [ "$set" = main ]; then
          for t in $(find_tests "$mod" "[A-Za-z0-9]*OnTomcat"); do add_test "${t%%:*}" "${t#*:}"; done
        fi
        if [ "$set" != main ] && [ "$set" != test ]; then
          other_sets=$(add_unique "$other_sets" "$mod:$set")
        fi
        if [ ! -e "$p" ]; then
          if [ "$set" = main ]; then removed_main=1; fi
        else
          class=$(basename "$p" .java)
          case "$class" in *Test) if [ "$set" = test ]; then add_test "$mod" "$class"; fi ;; esac
          for t in $(find_tests "$mod" "$class"); do add_test "${t%%:*}" "${t#*:}"; done
        fi
      fi
      ;;
    *.md)
      add_rules Fitness DocLinks DecisionIndex Features SpecLimits PracticesPage EngineeringSpec
      ;;
  esac
  case "$p" in
    .github/* | */.github/* | scripts/* | */scripts/*)
      add_rules CiWorkflow ImageJar QualityGates
      ;;
  esac
  case "$p" in
    *.java) ;;
    *.md)
      # A root test that reads a page names its path (2026-11-21: a master spec edit broke MasterRightsSpecTest unselected).
      for f in $(git grep --untracked -lF "$p" -- "$(test_dir root)" | grep -E 'Test\.java$' || true); do
        add_test root "$(basename "$f" .java)"
      done
      ;;
    *)
      # A root test that reads a file names it (2026-11-20: a .dist comment broke ServerStarterTest unselected).
      for f in $(git grep --untracked -lF "$(basename "$p")" -- "$(test_dir root)" | grep -E 'Test\.java$' || true); do
        add_test root "$(basename "$f" .java)"
      done
      ;;
  esac
  case "$p" in
    *.lockfile | */gradle-wrapper.properties) add_rules Pinning ;;
    */Dockerfile* | Dockerfile* | */entrypoint.sh | entrypoint.sh | */kubernetes.yml.dist | kubernetes.yml.dist)
      add_rules KubernetesManifests ImageJvmOptions
      ;;
    fitness.yml | */fitness.yml) add_rules Fitness ;;
    fitness/* | */fitness/*) fitness_touched=1; add_rules Fitness ;;
    board/*) board_touched=1 ;;
    site/*) add_rules Site ;;
    roles/*) add_rules RoleNamesFitness RoleNamesOutsideFitness RoleVocabulary MasterBriefShipped ;;
  esac
  case "$p" in
    *.yml.dist) add_rules ConfigReference ;;
  esac
done

gradle() { echo "cd $backend && timeout $1 ./gradlew $(printf "%s" "$2" | sed "s/^ *//")"; }

compile=""
check=""
for m in $touched_projects; do
  pre=$(prefix "$m")
  compile="$compile $pre:compileJava $pre:compileTestJava"
  check="$check $pre:checkstyleMain $pre:checkstyleTest"
done
for ms in $other_sets; do
  pre=$(prefix "${ms%%:*}")
  cap=$(capitalize "${ms#*:}")
  compile="$compile $pre:compile${cap}Java"
  check="$check $pre:checkstyle$cap"
done
if [ "$removed_main" = 1 ]; then
  for m in $modules root; do
    pre=$(prefix "$m")
    compile="$compile $pre:compileJava $pre:compileTestJava"
  done
  for s in $other_set_names; do
    compile="$compile :compile${s}Java"
  done
  compile="$compile :stand:compileStandJava"
fi

dedupe() { printf '%s\n' "$*" | tr ' ' '\n' | awk 'NF && !seen[$0]++' | tr '\n' ' ' | sed 's/ $//'; }

tests_line() { # entries "module:Class"
  line=""
  for m in root $modules; do
    names=""
    for e in $1; do
      if [ "${e%%:*}" = "$m" ]; then names="$names --tests '*${e#*:}'"; fi
    done
    if [ -n "$names" ]; then line="$line $(prefix "$m"):test$names"; fi
  done
  echo "$line"
}

if [ -n "$compile" ]; then gradle 600 "$(dedupe "$compile")"; fi
if [ -n "$check" ]; then gradle 600 "$(dedupe "$check")"; fi
if [ -n "$touched_tests" ]; then gradle 900 "$(tests_line "$touched_tests") -x pitestDiff"; fi

rule_entries=""
for r in $rule_names; do
  if case " $touched_tests " in *" root:${r}Test "*) true ;; *) false ;; esac; then
    continue
  elif [ -n "$(git ls-files -- "$backend/src/test/java/*/${r}.java")" ]; then
    rule_entries=$(add_unique "$rule_entries" root:FitnessTest)
  elif git ls-files --error-unmatch -- "$backend/src/test/java/dev/jagt/orchestrator/${r}Test.java" > /dev/null 2>&1; then
    rule_entries="$rule_entries root:${r}Test"
  else
    echo "tests-for: no rule test ${r}Test, dropped" >&2
  fi
done
if [ "$board_touched" = 1 ]; then rule_entries="$rule_entries surface:BoardAssetsTest root:FitnessTest"; fi
if [ -n "$rule_entries" ]; then gradle 900 "$(tests_line "$rule_entries") -x pitestDiff"; fi
if [ "$fitness_touched" = 1 ]; then echo "cd $backend/fitness && timeout 600 ../gradlew test"; fi
if [ "$board_touched" = 1 ]; then echo "cd board && timeout 600 sh -c 'npm run lint && npm test'"; fi
exit 0
