# Math and message controls

This is an implementation record. Some surrounding UI structure has changed since this work; see [README](README.md) for the current project overview and build instructions.

Java/AppCompat Views, minSdk 24. Markwon core/extensions/inline-parser/ext-latex are all 4.6.2. The downloaded ext-latex source was inspected: `JLatexMathPlugin.Builder.inlinesEnabled(true)`, `theme().textColor(...)`, `blockFitCanvas(true)` and `errorHandler(...)` are the actual APIs.

## Exact supported delimiters

* `$...$` and `\(...\)` are inline, with a complete closing delimiter on the same source line.
* `$$...$$` and `\[...\]` are display formulas, either same-line or multiline; complete pairs are converted to standalone Markwon math blocks.
* Fenced code (backticks/tilde, including unfinished fences), matched backtick code spans, and indented code are never normalized. Escaped dollar delimiters remain literal.
* Incomplete delimiters remain literal during streaming. Complete but invalid TeX uses the plugin's text placeholder on async failure rather than crashing. No HTML/image/network plugin was added and HTTP(S)-only links remain unchanged.
* Normalization does not alter stored messages or copied text. This is a conservative source scanner, not a full CommonMark container parser: deeply nested list/quote code fences and math embedded in link destinations are not specially supported. Ambiguous currency pairs may be interpreted as inline math; escape dollars to keep them literal.

## Controls

Each message has one bottom horizontal row: user edit-only / edit-and-retry / copy; assistant retry / edit / copy. Muted 18sp glyphs have 44dp touch targets and Chinese accessibility descriptions. Unsaved streaming assistant controls are disabled. Saved controls bind immutable session/message IDs and retain the existing busy/session guards. Re-render replaces rows; attachment views insert before controls and retain their click handlers.

Topbar is menu/title/subtitle only. New conversation is in a separate lower-right 48dp footer above the composer, within the same inset-managed vertical root (not a floating keyboard overlay). The history drawer retains its new-conversation button and has a pinned 52dp settings row outside its weighted history ScrollView. Existing empty-conversation reuse and timestamp behavior is unchanged.
