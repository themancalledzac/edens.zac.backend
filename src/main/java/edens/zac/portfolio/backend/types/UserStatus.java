package edens.zac.portfolio.backend.types;

public enum UserStatus {
  INVITED,
  ACTIVE,
  DISABLED,
  // A tag-only identity merged from content_people in V35: a person tagged in photos who has no
  // login account. Not an account lifecycle state -- excluded from the admin account list.
  PERSON
}
