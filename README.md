# IssueNotifier

IssueNotifier is application for Android that notifies you about issues from GitHub. This application requires Android 3.0 or newer.

## Compilation

You can compile this application by invoke the following command:

    sbt android:package-release

This application can be compiled with a debugging information by invoke the following command:

    sbt android:package-debug

After compilation, the issuenotifier-*.apk file is in the target directory.

## Launching

You can launch this application on a device by invoke the following command:

    sbt android:start-device

Also, you can launch this application on an emulator by invoke the following command:

    sbt android:start-emulator

## License

This application is licensed under the GNU General Public License v3 or later. See the LICENSE
file for the full licensing terms.
