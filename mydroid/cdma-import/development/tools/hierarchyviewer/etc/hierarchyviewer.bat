@echo off
rem Copyright (C) 2008 The Android Open Source Project
rem
rem Licensed under the Apache License, Version 2.0 (the "License");
rem you may not use this file except in compliance with the License.
rem You may obtain a copy of the License at
rem
rem      http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

rem don't modify the caller's environment
setlocal

rem Set up prog to be the path of this script, including following symlinks,
rem and set up progdir to be the fully-qualified pathname of its directory.
set prog=%~f0

rem Change current directory to where ddms is, to avoid issues with directories
rem containing whitespaces.
cd %~dp0

set jarfile=hierarchyviewer.jar
set frameworkdir=
set libdir=

if exist %frameworkdir%%jarfile% goto JarFileOk
    set frameworkdir=lib\

if exist %frameworkdir%%jarfile% goto JarFileOk
    set frameworkdir=..\framework\

:JarFileOk

set jarpath=%frameworkdir%%jarfile%

call java -Xmx512m -Djava.ext.dirs=%frameworkdir% -Dhierarchyviewer.adb= -jar %jarpath% %*
