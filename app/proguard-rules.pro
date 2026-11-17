# R8 rules for the release build.
#
# Hilt, Room, Compose, Navigation, DataStore and AndroidX ship their own consumer rules, and the
# manifest components (activity, accessibility service) are kept automatically. Add rules here
# only when a release build is shown to need one.

# Keep line numbers for readable crash reports from a release build, but hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
