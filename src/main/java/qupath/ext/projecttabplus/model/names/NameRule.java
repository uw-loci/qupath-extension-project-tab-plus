package qupath.ext.projecttabplus.model.names;

/**
 * One display-name rule. Rules only hide characters, so every character of a short name is a
 * character of the full name. A rule that is off, or unfinished, is skipped.
 */
public sealed interface NameRule permits PrefixRule, HideTextRule, HideExtensionRule, KeepPiecesRule, KeptRule {

    /** @return true when the rule is turned on */
    boolean on();

    /**
     * @param on the new state
     * @return a copy with that state
     */
    NameRule withOn(boolean on);

    /** @return false when a required field is empty, so the rule can do nothing yet */
    boolean finished();

    /** @return the JSON kind */
    String kind();

    /**
     * @param in the name as the rules above leave it
     * @param index this rule's position in its list, recorded against what it hides
     * @return the name after this rule
     */
    Mapped apply(Mapped in, int index);
}
