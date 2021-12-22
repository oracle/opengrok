class Main {

    private String[] argv;

    public Main(String[] argv) {
        this.argv = argv;
    }

    private void dump() {
        System.out.println("Started with the following arguments:");
        for (String s : argv) {
            System.out.print('[');
            System.out.print(s);
            System.out.print("] ");
        }
        System.out.println();
    }

    public static void main(String[] argv) {
        Main main = new Main(argv);
        main.dump();
    }
}
