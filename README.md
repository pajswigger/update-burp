# Update Burp

This program automatically updates Burp to a specific version, or the latest version. Usage:

    java -jar update-burp.jar [--version x.x.x] [--path /x/x/x]
    
You only need to specify the Burp path if installed in a non-default location.

The program identifies the current version of Burp, and uses the installed license to check for newer versions.
The platform installer is then downloaded and installed in quiet mode.

If Burp is running the install will normally fail. On Windows the installer needs to be run from an elevated prompt.