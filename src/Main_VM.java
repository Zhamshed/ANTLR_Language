import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class Main_VM {
    public static void main(String[] args) {
        try {
            List<String> code = Files.readAllLines(Paths.get("VMinput.txt"));
            Scanner inputScanner = new Scanner(System.in);
            System.out.println("=== Virtual Machine Output ===");
            VirtualMachine vm = new VirtualMachine(code, inputScanner);
            vm.run();
        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
        }
    }
}
