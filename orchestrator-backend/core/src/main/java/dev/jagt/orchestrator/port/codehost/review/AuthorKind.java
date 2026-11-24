package dev.jagt.orchestrator.port.codehost.review;

/**
 * Who wrote a note: the account jagt acts as, an account the host or the project lists as a bot, or
 * a person.
 */
public enum AuthorKind {
  ACTOR,
  BOT,
  PERSON
}
