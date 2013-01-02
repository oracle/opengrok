class Main {

   public static void main(String[] argv) {
      System.out.println("Started with the following arguments:");
      for (int i =  0; i < argv.length; ++i) {
         System.out.print('[');
         System.out.print(argv[i]);
         System.out.print("] ");
      }
      System.out.println();
   }
}
