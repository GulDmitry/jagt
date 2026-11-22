package dev.jagt.statechart;

import java.util.Set;

record TestIrpBuilder() {

  enum Node {
    S0,
    S01,
    S02,
    S1,
    S11,
    S12,
    PASS,
    FAIL
  }

  enum Peer {
    PS1,
    S1
  }

  record Ev(String name) {}

  static final RegionId<Peer> PEER = new RegionId<>("peer", Peer.class);

  static final RegionId<Node> NODE = new RegionId<>("node", Node.class);

  static StatechartBuilder<Ev, String, String> skeleton() {
    return Statechart.<Ev, String, String>builder()
        .compound(NODE, Node.S0, Node.S01, Node.S01, Node.S02)
        .compound(NODE, Node.S1, Node.S11, Node.S11, Node.S12)
        .onMatching(NODE, Node.S01, "next", e -> e.name().equals("next"))
        .to(Node.S02)
        .onMatching(NODE, Node.S11, "next", e -> e.name().equals("next"))
        .to(Node.S12)
        .onMatching(NODE, Node.S0, "bad", e -> e.name().equals("bad"))
        .to(Node.FAIL)
        .onMatching(NODE, Node.S0, "enter1", e -> e.name().equals("enter1"))
        .to(Node.S1)
        .onMatching(NODE, Node.S1, "end", e -> e.name().equals("end"))
        .to(Node.PASS);
  }

  static Set<Node> finals() {
    return Set.of(Node.PASS, Node.FAIL);
  }
}
