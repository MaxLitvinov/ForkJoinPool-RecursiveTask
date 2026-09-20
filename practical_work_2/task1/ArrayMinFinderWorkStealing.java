import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Варіант 3, задача 1.
 * Пошук мінімального елемента у двовимірному масиві серед тих елементів,
 * значення яких вдвічі більше (>=) за перший згенерований елемент масиву.
 *
 * Реалізація через Fork/Join Framework -> підхід Work Stealing.
 * ForkJoinPool за замовчуванням реалізує роботу за принципом "крадіжки задач":
 * кожен потік має власну чергу задач (deque), і коли черга потоку порожня,
 * він "краде" задачі з кінця черг інших, ще зайнятих потоків.
 */
public class ArrayMinFinderWorkStealing {

    // Поріг: якщо кількість елементів у підзадачі менша за нього,
    // подальше дроблення (fork) припиняється і рахуємо напряму.
    static final int THRESHOLD = 2000;

    public static void main(String[] args) {
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

        ForkJoinPool pool = new ForkJoinPool(); // Work Stealing реалізовано за замовчуванням

        long startTime = System.nanoTime();
        MinFinderTask rootTask = new MinFinderTask(array, 0, rows, threshold);
        Result result = pool.invoke(rootTask);
        long endTime = System.nanoTime();

        pool.shutdown();

        printResult(result, threshold);
        System.out.printf("Кількість потоків пулу (паралелізм): %d%n", pool.getParallelism());
        System.out.printf("Час виконання (Work Stealing, ForkJoinPool): %.3f мс%n",
                (endTime - startTime) / 1_000_000.0);

        scanner.close();
    }

    /** Результат пошуку: мінімальне значення, що задовольняє умову, та його координати. */
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

    /** Рекурсивна задача Fork/Join: ділить діапазон рядків масиву навпіл. */
    static class MinFinderTask extends RecursiveTask<Result> {
        final int[][] arr;
        final int startRow;
        final int endRow;
        final long threshold;

        MinFinderTask(int[][] arr, int startRow, int endRow, long threshold) {
            this.arr = arr;
            this.startRow = startRow;
            this.endRow = endRow;
            this.threshold = threshold;
        }

        @Override
        protected Result compute() {
            int rowCount = endRow - startRow;
            int colCount = arr.length > 0 ? arr[0].length : 0;
            long totalElements = (long) rowCount * colCount;

            if (totalElements <= THRESHOLD || rowCount <= 1) {
                return computeDirectly();
            }

            int mid = startRow + rowCount / 2;
            MinFinderTask leftTask = new MinFinderTask(arr, startRow, mid, threshold);
            MinFinderTask rightTask = new MinFinderTask(arr, mid, endRow, threshold);

            // fork() кладе задачу у власну чергу потоку; її може "вкрасти" інший вільний потік
            leftTask.fork();
            Result rightResult = rightTask.compute(); // цю задачу виконує поточний потік
            Result leftResult = leftTask.join();       // чекаємо (або доробляємо) результат

            return leftResult.merge(rightResult);
        }

        private Result computeDirectly() {
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
