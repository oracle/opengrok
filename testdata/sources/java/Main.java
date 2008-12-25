/**
 * Javadoc comment
 *
 * @author someone@axample.com
 */
class Main {

    private String[] argv;

    public Main(String[] argv) {
        this.argv = argv;
    }

    /*
     * Ordinary comment
     */
    private void dump() {
        System.out.println("Started with the following arguments:");
        for (String s : argv) {
            System.out.print('[');
            System.out.print(s);
            System.out.print("] ");
            System.out.println("String with embedded \", ', /* and // symbols.");
            System.out.println("String with symbols that has to be encoded <, >, <--. &.");
        }
        // One line comment
        System.out.println();
    }

    /**
     * Just an example javadoc comment.
     *
     * See <filename.java>, or http://www.example.com/file.html
     * 
     * @param argv The parameter list
     * @throws java.lang.Exception
     */
    @Deprecated
    public static void main(String[] argv) throws Exception {
        Main main = new Main(argv);
        main.dump();
        if (false) {
            throw new Exception("Some string");
        }
        int result = 123 + 456;
        System.exit(result);
    }
}
