# CreditPincher

![Build](https://github.com/VladislavEkimtcov/CreditPincher/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

CreditPincher adds an **AI Use** tool window to the right sidebar of JetBrains IDEs so you can track local inference-credit usage even when your provider does not expose usage stats.

## Features

- Log a floating-point credit amount with a submit button or by pressing <kbd>Enter</kbd>
- Explore usage using a calendar-backed start/end date range that defaults to month-to-date
- Store a self-imposed monthly budget locally
- Review stats such as:
  - total credits
  - average credits per day
  - average credits per active day
  - busiest day
  - highest single entry
  - budget consumed in the selected range
  - projected month total
  - projected budget runout day or projected under-budget amount
- View the local storage directory and recent entries directly in the tool window

## Local storage

CreditPincher stores its data in a plain local directory:

`~/.creditpincher`

The folder contains human-readable files for:

- `monthly-budget.txt`
- `usage-log.csv`

Because the files live in your home directory, they are shared across JetBrains IDEs on the same machine and can be backed up by copying that folder elsewhere.

## Development

Run the IDE sandbox:

```bash
./gradlew runIde
```

Run tests:

```bash
./gradlew test
```

## Installation

- Manually:

  Download the [latest release](https://github.com/VladislavEkimtcov/CreditPincher/releases/latest) or compile with `./gradlew buildPlugin` and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
