import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Варіант 3, задача 1.
 * Пошук мінімального елемента у двовимірному масиві серед тих елементів,
 * значення яких вдвічі більше (>=) за перший згенерований елемент масиву.
 *
 * Реалізація через ExecutorService / Thread Pool -> підхід Work Dealing.
 * Масив рядків заздалегідь ("наперед") ділиться на рівні блоки за кількістю
 * потоків, і кожен потік отримує свій фіксований блок роботи. Потоки НЕ можуть
 * забирати роботу один в одного ("красти") - розподіл статичний і виконується
 * головним потоком (диспетчером) один раз перед запуском обчислень.
 */
public class ArrayMinFinderWorkDealing {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Scanner scanner = new Scanner(System.in);

        int rows = readPositiveInt(scanner, "Введіть кількість рядків масиву: ");
        int cols = readPositiveInt(scanner, "Введіть кількість стовпців масиву: ");
        int minVal = readInt(scanner, "Введіть мінімальне значення елементів: ");
        int maxVal = readIntGreaterThan(scanner, minVal,
                "Введіть максимальне значення елементів (більше за мінімальне): ");

        int[][] array = generateArray(rows, cols, minVal, maxVal);
        printArray(array);

        int firstElement = array[0][0];
        long threshold = 2L * firstElement;
        System.out.println("Перший елемент масиву a[0][0] = " + firstElement);
        System.out.println("Шукаємо мінімум серед елементів зі значенням >= " + threshold);

        int threadCount = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // "Дилер" наперед ділить рядки масиву на рівні блоки - по одному на кожен потік
        int rowsPerThread = (int) Math.ceil((double) rows / threadCount);
        List<Future<Result>> futures = new ArrayList<>();

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            int startRow = t * rowsPerThread;
            int endRow = Math.min(startRow + rowsPerThread, rows);
            if (startRow >= endRow) continue;
            futures.add(executor.submit(new MinFinderWorker(array, startRow, endRow, threshold)));
        }

        Result finalResult = new Result();
        for (Future<Result> future : futures) {
            finalResult = finalResult.merge(future.get());
        }

        long endTime = System.nanoTime();
        executor.shutdown();

        printResult(finalResult, threshold);
        System.out.printf("Кількість потоків пулу (наперед розподілених блоків): %d%n", threadCount);
        System.out.printf("Час виконання (Work Dealing, fixed ThreadPool): %.3f мс%n",
                (endTime - startTime) / 1_000_000.0);

        scanner.close();
    }

    static class Result {
        int minValue = Integer.MAX_VALUE;
        int row = -1;
        int col = -1;
        boolean found = false;

        Result merge(Result other) {
            if (!other.found) return this;
            if (!this.found || other.minValue < this.minValue) return other;
            return this;
        }
    }

    /** Задача, що обробляє один наперед виділений блок рядків - без подальшого дроблення. */
    static class MinFinderWorker implements Callable<Result> {
        final int[][] arr;
        final int startRow;
        final int endRow;
        final long threshold;

        MinFinderWorker(int[][] arr, int startRow, int endRow, long threshold) {
            this.arr = arr;
            this.startRow = startRow;
            this.endRow = endRow;
            this.threshold = threshold;
        }

        @Override
        public Result call() {
            Result result = new Result();
            for (int i = startRow; i < endRow; i++) {
                for (int j = 0; j < arr[i].length; j++) {
                    if (arr[i][j] >= threshold) {
                        if (!result.found || arr[i][j] < result.minValue) {
                            result.minValue = arr[i][j];
                            result.row = i;
                            result.col = j;
                            result.found = true;
                        }
                    }
                }
            }
            return result;
        }
    }

    // ---------- Допоміжні методи (спільні для обох версій) ----------

    static int[][] generateArray(int rows, int cols, int minVal, int maxVal) {
        Random random = new Random();
        int[][] array = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                array[i][j] = minVal + random.nextInt(maxVal - minVal + 1);
            }
        }
        return array;
    }

    static void printArray(int[][] array) {
        System.out.println("Згенерований масив:");
        int maxRowsToPrint = Math.min(array.length, 20);
        for (int i = 0; i < maxRowsToPrint; i++) {
            StringBuilder sb = new StringBuilder();
            int maxColsToPrint = Math.min(array[i].length, 20);
            for (int j = 0; j < maxColsToPrint; j++) {
                sb.append(array[i][j]).append('\t');
            }
            if (array[i].length > maxColsToPrint) sb.append("...");
            System.out.println(sb);
        }
        if (array.length > maxRowsToPrint) {
            System.out.println("... (виведено перші " + maxRowsToPrint + " рядків із " + array.length + ")");
        }
    }

    static void printResult(Result result, long threshold) {
        if (result.found) {
            System.out.printf("Мінімальний елемент, що задовольняє умову (>= %d): %d, позиція [%d][%d]%n",
                    threshold, result.minValue, result.row, result.col);
        } else {
            System.out.println("Елементів, що задовольняють умову, не знайдено.");
        }
    }

    static int readPositiveInt(Scanner scanner, String prompt) {
        int value;
        while (true) {
            System.out.print(prompt);
            if (scanner.hasNextInt()) {
                value = scanner.nextInt();
                if (value > 0) return value;
                System.out.println("Значення має бути додатнім. Спробуйте ще раз.");
            } else {
                System.out.println("Некоректне введення. Введіть ціле число.");
                scanner.next();
            }
        }
    }

    static int readInt(Scanner scanner, String prompt) {
        System.out.print(prompt);
        while (!scanner.hasNextInt()) {
            System.out.println("Некоректне введення. Введіть ціле число.");
            scanner.next();
            System.out.print(prompt);
        }
        return scanner.nextInt();
    }

    static int readIntGreaterThan(Scanner scanner, int lowerBound, String prompt) {
        int value;
        while (true) {
            System.out.print(prompt);
            if (scanner.hasNextInt()) {
                value = scanner.nextInt();
                if (value > lowerBound) return value;
                System.out.println("Значення має бути більшим за " + lowerBound + ". Спробуйте ще раз.");
            } else {
                System.out.println("Некоректне введення. Введіть ціле число.");
                scanner.next();
            }
        }
    }
}
