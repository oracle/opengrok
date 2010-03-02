REM Get the name of the directory this run.bat script is living in.
set PROGDIR=%~dp0

REM REQUIRED The root of your source tree (upper-case drive
REM letter matters)
set SRC_ROOT=C:\your\source\root

REM REQUIRED The directory where the data files like Lucene index and
REM hypertext cross-references are stored
set DATA_ROOT=C:\your\data\root

REM REQUIRED if ctags is not in PATH
REM A modern Exubrant Ctags program
REM from http://ctags.sf.net
set EXUB_CTAGS=C:\ctags\ctags.exe

REM OPTIONAL 
REM A tab separated file that contains small
REM descriptions for paths in the source tree
set PATH_DESC=%PROGDIR%\paths.tsv

set LOGGER="-Djava.util.logging.config.file=logging.properties"

java %LOGGER% -jar %PROGDIR%opengrok.jar -c %EXUB_CTAGS% -s %SRC_ROOT% -d %DATA_ROOT%

REM OPTIONAL
java %LOGGER% -classpath %PROGDIR%opengrok.jar org.opensolaris.opengrok.web.EftarFile %PATH_DESC% %DATA_ROOT%\index\dtags.eftar
