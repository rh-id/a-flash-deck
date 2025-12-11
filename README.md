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

## Architecture

The app follows a modern Android architecture, utilizing a combination of established libraries and custom frameworks to create a modular and maintainable codebase.

*   **Dependency Injection**: The app uses a custom service locator pattern with the `a-provider` library. A global `Provider` is initialized in the `MainApplication` class, which is then used to provide dependencies throughout the app. This creates a centralized and easy-to-manage dependency graph.

*   **Navigation**: Navigation between screens is handled by the `a-navigator` library. This library provides a flexible and powerful way to manage navigation, including support for different screen types and transitions.

*   **Reactive Programming**: The app makes extensive use of `RxJava` for handling asynchronous operations and UI events. This allows for a more concise and readable code, especially when dealing with complex asynchronous workflows.

*   **Modular Design**: The app is divided into several modules, each with a specific responsibility. This promotes a clean separation of concerns and makes the codebase easier to understand and maintain.

*   **Error Handling**: A global exception handler is set up in `MainApplication` to log crashes and other unexpected errors. This helps to ensure that the app is as stable as possible.

*   **Background Jobs**: Background tasks are handled by `WorkManager`, with a custom configuration provided by `MainApplication`. This allows for efficient and reliable execution of background tasks, such as syncing data or sending notifications.

### Presentation Layer: The `StatefulView` Pattern

The presentation layer is built on a custom component-based architecture centered around the `StatefulView` class. This pattern deviates from traditional MVP or MVVM in favor of a more self-contained and reactive approach.

Here’s a breakdown of the workflow:

*   **View and Logic Combined**: `StatefulView` classes (e.g., `HomePage`, `SettingsPage`) are responsible for both creating the Android `View` and handling the presentation logic. This makes each `StatefulView` a self-contained UI component.

*   **Lifecycle**: The `a-navigator` library manages the lifecycle of `StatefulView`s. When you navigate to a new screen, the navigator creates the corresponding `StatefulView` instance. The `dispose` method is then called when the view is no longer needed, which is crucial for unsubscribing from RxJava streams and preventing memory leaks.

*   **Dependency Injection**: Dependencies are injected in two ways:
    1.  The navigator injects navigation-related components (like `INavigator` and `AppBarSV`) using the `@NavInject` annotation.
    2.  Other dependencies (like data sources, commands, and notifiers) are provided by the `a-provider` service locator via the `provideComponent` method.

*   **UI Creation**: The `createView` method is where the UI is constructed. It inflates an XML layout, finds `View`s by their IDs, and sets up event listeners. It also subscribes to RxJava streams to react to state changes.

*   **State Management and Reactivity**: The UI state is managed using RxJava’s `BehaviorSubject`. The UI elements subscribe to these subjects, so whenever the state changes (e.g., a test starts or stops), the UI updates automatically and reactively.

*   **User Interaction**: User actions, handled in methods like `onClick`, trigger business logic by calling command classes (e.g., `mNewCardCmd`, `mTestStateModifier.startTest`). These commands perform operations and update the state, which in turn updates the UI through the reactive streams.

## Workflow

The application's workflow is designed to be modular, scalable, and reactive. It follows a clear separation of concerns, with distinct layers for the UI, business logic, and data.

### Data Layer

The data layer is responsible for all data-related operations. It is built on top of the following components:

*   **Room Persistence Library**: The app uses Room for local data persistence. It has two databases:
    *   `AppDatabase`: The main database for the app, managing entities like `Deck`, `Card`, `Test`, `AndroidNotification`, and `NotificationTimer`.
    *   `BotDatabase`: A separate database for the "Flash bot" feature, managing entities like `CardLog` and `SuggestedCard`.
*   **DAOs (Data Access Objects)**: Each entity has a corresponding DAO that defines the methods for accessing and manipulating the data in the database.

### Business Logic: The Command Pattern

The business logic is encapsulated in command classes, which follow the command pattern. These commands are responsible for executing specific business operations, such as creating a new deck or updating a card.

Some of the key command classes include:

*   `NewDeckCmd`: Creates a new deck.
*   `UpdateCardCmd`: Updates an existing card.
*   `DeleteDeckCmd`: Deletes a deck.
*   `ExportImportCmd`: Handles the export and import of decks.

These commands are provided by the `CommandProviderModule` and are injected into the `StatefulView`s where they are needed.

### End-to-End Data Flow

The app's data flow is designed to be unidirectional and reactive, ensuring that the UI is always in sync with the underlying data. Here's a step-by-step overview of the data flow:

1.  **User Interaction**: The user interacts with a `StatefulView` (e.g., clicks a button).
2.  **Command Execution**: The `StatefulView` invokes the appropriate command to handle the user's action.
3.  **Data Manipulation**: The command interacts with the data layer (via the DAOs) to create, read, update, or delete data.
4.  **State Notification**: After the data is updated, the command uses a notifier (e.g., `DeckChangeNotifier`) to broadcast that the data has changed.
5.  **UI Update**: The `StatefulView`s subscribe to these notifiers and update their UI in response to the change notifications. This is done reactively using `RxJava`, ensuring that the UI always reflects the current state of the data.

### Threading

To ensure that the UI remains responsive, all database and business logic operations are performed on background threads. This is achieved through a combination of `RxJava` schedulers and a dedicated `ExecutorService`, which is provided by the `BaseProviderModule`.

## Testing

The project includes both unit and instrumentation tests, although the coverage could be improved. Here's a summary of the testing strategy:

*   **Unit Tests**: The project has a `unitTest` artifact, but it currently only contains a boilerplate example. This is an area for improvement, as the business logic in the command classes and other components could be unit-tested with mocked dependencies.

*   **Instrumentation Tests**: The project has an `androidTest` artifact with at least one meaningful test, `ExportImportCmdTest.java`. This test demonstrates a good approach to testing database interactions and file operations, using a separate `Provider` and an in-memory database to ensure that tests are hermetic and isolated.

*   **Areas for Improvement**: In addition to adding more unit tests, the project would benefit from UI tests using a framework like Espresso. This would allow for the verification of the application's user interface and user flows, ensuring that the app behaves as expected from the user's perspective.

## CI/CD and Automation

The project uses a combination of GitHub Actions and Fastlane to automate the build, test, and release process.

### GitHub Actions

The project has three GitHub Actions workflows:

*   `gradlew-build.yml`: Builds the project with Gradle on every push and pull request to the `master` branch.
*   `android-release.yml`: Creates a GitHub release and attaches the debug and release APKs when a new tag starting with "v" is pushed.
*   `android-emulator-test.yml`: Runs Android instrumentation tests on an emulator on every push and pull request to the `master` branch.

### Fastlane

The project uses Fastlane to manage the app's metadata for the Google Play Store. This includes the app's title, description, screenshots, and changelogs. The metadata is stored in the `fastlane/metadata` directory and is organized by language.


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
