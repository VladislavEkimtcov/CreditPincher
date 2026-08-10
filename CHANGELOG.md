<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# CreditPincher Changelog

## [Unreleased]
### Added
- Conflicted backup files can now be resolved in the IDE's own three-way merge window, with local changes on the left, the incoming remote changes on the right, and a fully editable result pane in the middle
- Conflict sides are read from the git index (`git show :1:`/`:2:`/`:3:`), falling back to parsing the `<<<<<<<`/`=======`/`>>>>>>>` markers in the working tree
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

### Changed
- Cancelling the merge window now aborts the in-progress merge/rebase instead of committing a half-resolved tree
