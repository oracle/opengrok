Known Issues Outside of OpenGrok ...
Edit

    Due to Exuberant ctags bugs 1187505, 2991345, 2996602 ctags fails to recognize certain definitions. So OpenGrok will not be able search these
    Indexing is a memory intensive process. If you get "java.lang.OutOfMemoryError: Java heap space" error, try the -Xms<size> option to java (Eg java -Xms128m)
    In Mozilla based browsers (e.g. Firefox) the back button will not take you to previous anchor - gecko bug 565008 - WORKAROUND is simple, after you press back, just do a refresh (F5, Ctrl+R, or hit the reload button)
    Identifiers which have length of 1 character don't have a link generated - this is actually by purpose ...
    Due to Exuberant ctags bug 1324663, OpenGrok might keep waiting for ctags. Workaround is to ignore the problem causing SQL files with -i option to OpenGrok.(fixed in exuberant ctags >= 5.7)
    We disabled java local variables in 0.10 because of ctags bug 3150230

OpenGrok Issues, Bugs and Requests for Enhancements
Edit

 Please log bugs and requests for enhancements here: XXX