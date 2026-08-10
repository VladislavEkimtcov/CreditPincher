<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# CreditPincher Changelog

## [Unreleased]
### Added
- Conflicted backup files can now be resolved in the IDE's own three-way merge window, with local changes on the left, the incoming remote changes on the right, and a fully editable result pane in the middle
- Conflict sides are read from the git index (`git show :1:`/`:2:`/`:3:`), falling back to parsing the `<<<<<<<`/`=======`/`>>>>>>>` markers in the working tree
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

- The git backup panel now reports the detected `origin`, branch, ahead/behind counts and dirty state of a storage directory that is already a git repository, and offers a "Refresh status" action
- `git push` now establishes the upstream branch when the current branch does not track one yet

### Changed
- Cancelling the merge window now aborts the in-progress merge/rebase instead of committing a half-resolved tree
- Editing the remote URL of an existing storage repository now only retargets `origin`, instead of re-running the initialize flow that force-renamed the branch to `main` and silently replaced the configured remote

### Fixed
- The git backup panel could stay stuck on "Checking git status…" with every control disabled: the status check had no failure path, and its result was posted with a non-modal modality state that withheld it while any modal dialog was open
