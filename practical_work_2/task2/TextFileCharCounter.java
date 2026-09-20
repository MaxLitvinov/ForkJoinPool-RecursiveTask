import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Варіант 3, задача 2.
 * Програма проходить по файлах вказаної директорії (рекурсивно, включно
 * з піддиректоріями), знаходить файли текстового формату, читає їх вміст
 * і підраховує кількість символів у кожному.
 *
 * Реалізовано через Fork/Join Framework з підходом Work Stealing:
 * структура директорій - природно рекурсивна (дерево), тому для кожної
 * піддиректорії створюється власна задача (RecursiveTask), яка ставиться
 * в чергу поточного потоку через fork(). Якщо якийсь потік пулу звільняється
 * раніше за інших, він "краде" задачі з черг ще завантажених потоків -
 * це і є Work Stealing.
 */
public class TextFileCharCounter {

    // Розширення, які вважаємо текстовими файлами
    static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "csv", "log", "java", "json", "xml", "html", "css", "js", "properties", "yml", "yaml"
    );

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Path dir = readDirectory(scanner);

        ForkJoinPool pool = new ForkJoinPool(); // Work Stealing реалізовано за замовчуванням

        long startTime = System.nanoTime();
        DirectoryScanTask rootTask = new DirectoryScanTask(dir);
        List<FileResult> results = pool.invoke(rootTask);
        long endTime = System.nanoTime();

        pool.shutdown();

        if (results.isEmpty()) {
            System.out.println("Текстових файлів у вказаній директорії не знайдено.");
        } else {
            System.out.println("Знайдені текстові файли:");
            long totalChars = 0;
            for (FileResult r : results) {
                System.out.printf("  %-60s %,10d символів%n", r.path, r.charCount);
                totalChars += r.charCount;
            }
            System.out.println("----------------------------------------------------------------------");
            System.out.printf("Кількість знайдених файлів: %d%n", results.size());
            System.out.printf("Загальна кількість символів: %,d%n", totalChars);
        }

        System.out.printf("Кількість потоків пулу (паралелізм): %d%n", pool.getParallelism());
        System.out.printf("Час виконання (Work Stealing, ForkJoinPool): %.3f мс%n",
                (endTime - startTime) / 1_000_000.0);

        scanner.close();
    }

    static Path readDirectory(Scanner scanner) {
        while (true) {
            System.out.print("Введіть шлях до директорії: ");
            String input = scanner.nextLine().trim();
            Path path = Paths.get(input);
            if (Files.exists(path) && Files.isDirectory(path)) {
                return path;
            }
            System.out.println("Директорія не знайдена або шлях некоректний. Спробуйте ще раз.");
        }
    }

    /** Результат обробки одного файлу: шлях та кількість символів. */
    static class FileResult {
        final String path;
        final long charCount;

        FileResult(String path, long charCount) {
            this.path = path;
            this.charCount = charCount;
        }
    }

    /** Рекурсивна задача Fork/Join: обробляє одну директорію, форкаючи піддиректорії. */
    static class DirectoryScanTask extends RecursiveTask<List<FileResult>> {
        final Path directory;

        DirectoryScanTask(Path directory) {
            this.directory = directory;
        }

        @Override
        protected List<FileResult> compute() {
            List<FileResult> results = new ArrayList<>();
            List<DirectoryScanTask> subTasks = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        DirectoryScanTask subTask = new DirectoryScanTask(entry);
                        subTask.fork(); // задача потрапляє у чергу; її може "вкрасти" інший потік
                        subTasks.add(subTask);
                    } else if (isTextFile(entry)) {
                        long count = countCharacters(entry);
                        results.add(new FileResult(entry.toString(), count));
                    }
                }
            } catch (IOException e) {
                System.err.println("Помилка читання директорії " + directory + ": " + e.getMessage());
            }

            for (DirectoryScanTask subTask : subTasks) {
                results.addAll(subTask.join());
            }

            return results;
        }
    }

    static boolean isTextFile(Path file) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == name.length() - 1) return false;
        String ext = name.substring(dotIndex + 1).toLowerCase();
        return TEXT_EXTENSIONS.contains(ext);
    }

    static long countCharacters(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return content.length();
        } catch (IOException e) {
            System.err.println("Не вдалося прочитати файл " + file + ": " + e.getMessage());
            return 0;
        }
    }
}
