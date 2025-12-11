# a-flash-deck

![Languages](https://img.shields.io/github/languages/top/rh-id/a-flash-deck)
![Downloads](https://img.shields.io/github/downloads/rh-id/a-flash-deck/total)
![GitHub release (by tag)](https://img.shields.io/github/downloads/rh-id/a-flash-deck/latest/total)
![Release](https://img.shields.io/github/v/release/rh-id/a-flash-deck)
![Android CI](https://github.com/rh-id/a-flash-deck/actions/workflows/gradlew-build.yml/badge.svg)
![Release Build](https://github.com/rh-id/a-flash-deck/actions/workflows/android-release.yml/badge.svg)
![Emulator Test](https://github.com/rh-id/a-flash-deck/actions/workflows/android-emulator-test.yml/badge.svg)

A simple and easy to use flash card app to help you study.

## Screenshots
<img src="https://github.com/rh-id/a-flash-deck/blob/master/fastlane/metadata/android/en-US/images/featureGraphic.png" width="1024"/>

<img src="https://github.com/rh-id/a-flash-deck/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" height="512"/>
<img src="https://github.com/rh-id/a-flash-deck/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" height="512"/>
<img src="https://github.com/rh-id/a-flash-deck/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" height="512"/>
<img src="https://github.com/rh-id/a-flash-deck/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" height="512"/>
<img src="https://github.com/rh-id/a-flash-deck/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" height="512"/>

## Features
* Easily add deck and cards
* Add notification timer to periodically asking you question
* Support dark mode and light mode
* Easily export & share your decks to your friends
* Record voices and attach images for the cards
* Create shortcut to show random card from deck for casual study (Android 8 and above)
* Flash bot to smartly suggest list of card to test you

## Project Structure

This project is a multi-module Android application.

*   `:app`: The main application module that contains the UI and presentation layer.
*   `:base`: A library module that contains base classes and utilities shared across other modules.
*   `:bot`: A library module that contains the logic for the "Flash bot" feature.
*   `:timer-notification`: A library module for handling timer-based notifications.

This project is intended for demo app for [a-navigator](https://github.com/rh-id/a-navigator) and [a-provider](https://github.com/rh-id/a-provider) library usage. The app still works as production even though it is demo app.

## How to Build

1.  Clone the repository: `git clone https://github.com/rh-id/a-flash-deck.git`
2.  Open the project in Android Studio.
3.  Build the project using Gradle: `./gradlew assembleDebug`

## Libraries Used

The app uses [a-navigator](https://github.com/rh-id/a-navigator) framework as navigator and `StatefulView` as base structure, combined with [a-provider](https://github.com/rh-id/a-provider) library for service locator, and finally RxAndroid to handle UI use cases.

## Support this project
Consider donation to support this project
<table>
  <tr>
    <td><a href="https://teer.id/rh-id">https://teer.id/rh-id</a></td>
  </tr>
</table>
