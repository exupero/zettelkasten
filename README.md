# Zettelkasten

To run, install [Babashka](https://babashka.org/) and run `bb serve -d PATH`, where `PATH` is the directory that contains your zettelkasten notes as Markdown files.

Use Markdown's named link syntax to create tags, e.g.

```
[a tag]:
[another tag]:
```

The Markdown parser doesn't do anything special with this syntax, but my text editor's syntax highlighting does show it differently.
