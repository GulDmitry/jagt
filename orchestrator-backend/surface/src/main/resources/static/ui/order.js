// In what ORDER the cards sit. Registration order is the default because it never moves a card that is already
// on screen: an alias is the lowest free number, so a task created after one was retired takes the retired
// one's place in an alias order and lands in the middle of the board.

let byAlias = false;

export const holds = () => byAlias;

export const toggle = () => {
  byAlias = !byAlias;
};

// An alias-less task sorts after every aliased one, as `-` reads on the card. `numeric` is what puts p2 before p10.
const compare = (a, b) => (a.alias ? 0 : 1) - (b.alias ? 0 : 1)
  || (a.alias || '').localeCompare(b.alias || '', undefined, {numeric: true});

export const sorted = (tasks) => (byAlias ? [...tasks].sort(compare) : tasks);
