import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class JeymzCash {
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public static void main(String[] args) {
		WalletService wallet = new WalletService();
		wallet.register("09922030061", "Jeymz Pogi", new BigDecimal("5000.00"));
		wallet.register("09603897249", "JM Pogi P. Din", new BigDecimal("1200.00"));

		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("=== JeymzCash Mobile Wallet ===");
			System.out.println("Demo accounts: 09922030061 (Jeymz) and 09603897249 (JM Pogi P. Din)");
			WalletAccount current = chooseAccount(scanner, wallet);

			while (current != null) {
				printMenu(current);
				String choice = prompt(scanner, "Choose an option: ");
				if (choice == null) {
					break;
				}

				try {
					switch (choice) {
						case "1" -> showBalance(current);
						case "2" -> {
							wallet.cashIn(current.getMobileNumber(), readAmount(scanner));
							System.out.println("Cash-in complete.");
						}
						case "3" -> {
							String recipient = readRequired(scanner, "Recipient mobile number: ");
							BigDecimal amount = readAmount(scanner);
							wallet.sendMoney(current.getMobileNumber(), recipient, amount);
							System.out.println("Money sent successfully.");
						}
						case "4" -> {
							String biller = readRequired(scanner, "Biller name: ");
							BigDecimal amount = readAmount(scanner);
							wallet.payBill(current.getMobileNumber(), biller, amount);
							System.out.println("Bill payment complete.");
						}
						case "5" -> {
							String recipient = readRequired(scanner, "Mobile number to load: ");
							BigDecimal amount = readAmount(scanner);
							wallet.buyLoad(current.getMobileNumber(), recipient, amount);
							System.out.println("Mobile load purchase complete.");
						}
						case "6" -> showHistory(current);
						case "7" -> current = chooseAccount(scanner, wallet);
						case "8" -> current = null;
						default -> System.out.println("Please choose a number from 1 to 8.");
					}
				} catch (WalletException | IllegalArgumentException e) {
					System.out.println("Transaction not completed: " + e.getMessage());
				}
			}
			System.out.println("Thanks for using JeymzCash.");
		}
	}

	private static void printMenu(WalletAccount account) {
		System.out.printf("%nSigned in: %s (%s)%n", account.getName(), account.getMobileNumber());
		System.out.println("1. View balance");
		System.out.println("2. Cash in (simulated)");
		System.out.println("3. Send money");
		System.out.println("4. Pay a bill");
		System.out.println("5. Buy mobile load");
		System.out.println("6. Transaction history");
		System.out.println("7. Switch account");
		System.out.println("8. Exit");
	}

	private static WalletAccount chooseAccount(Scanner scanner, WalletService wallet) {
		while (true) {
			String mobileNumber = readRequired(scanner, "Sign in with a demo mobile number: ");
			try {
				return wallet.findAccount(mobileNumber);
			} catch (WalletException e) {
				System.out.println(e.getMessage());
			}
		}
	}

	private static void showBalance(WalletAccount account) {
		System.out.printf(Locale.US, "Available balance: PHP %,.2f%n", account.getBalance());
	}

	private static void showHistory(WalletAccount account) {
		List<Transaction> history = account.getTransactionHistory();
		if (history.isEmpty()) {
			System.out.println("No transactions yet.");
			return;
		}

		System.out.println("=== Transaction History ===");
		for (Transaction transaction : history) {
			System.out.printf(Locale.US, "%s | %-12s | PHP %,.2f | %s | Ref: %s%n",
					transaction.timestamp().format(TIMESTAMP_FORMAT), transaction.type(), transaction.amount(),
					transaction.description(), transaction.reference());
		}
	}

	private static BigDecimal readAmount(Scanner scanner) {
		while (true) {
			String value = readRequired(scanner, "Amount in PHP: ");
			try {
				return new BigDecimal(value);
			} catch (NumberFormatException e) {
				System.out.println("Enter a valid amount, for example 250.00.");
			}
		}
	}

	private static String readRequired(Scanner scanner, String message) {
		while (true) {
			String value = prompt(scanner, message);
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
			System.out.println("This field is required.");
		}
	}

	private static String prompt(Scanner scanner, String message) {
		System.out.print(message);
		return scanner.hasNextLine() ? scanner.nextLine().trim() : null;
	}

	private enum TransactionType {
		CASH_IN, SEND_MONEY, RECEIVE_MONEY, BILL_PAYMENT, MOBILE_LOAD
	}

	private record Transaction(String reference, TransactionType type, BigDecimal amount,
							   LocalDateTime timestamp, String description) {
		private Transaction(TransactionType type, BigDecimal amount, String description) {
			this(UUID.randomUUID().toString().substring(0, 8), type, amount, LocalDateTime.now(), description);
		}
	}

	private static final class WalletAccount {
		private final String mobileNumber;
		private final String name;
		private final ReentrantLock lock = new ReentrantLock();
		private final List<Transaction> transactions = new ArrayList<>();
		private BigDecimal balance;

		private WalletAccount(String mobileNumber, String name, BigDecimal initialBalance) {
			this.mobileNumber = mobileNumber;
			this.name = name;
			this.balance = initialBalance;
			addTransaction(TransactionType.CASH_IN, initialBalance, "Opening wallet balance");
		}

		private String getMobileNumber() {
			return mobileNumber;
		}

		private String getName() {
			return name;
		}

		private BigDecimal getBalance() {
			lock.lock();
			try {
				return balance;
			} finally {
				lock.unlock();
			}
		}

		private List<Transaction> getTransactionHistory() {
			lock.lock();
			try {
				List<Transaction> snapshot = new ArrayList<>(transactions);
				snapshot.sort(Comparator.comparing(Transaction::timestamp).reversed());
				return List.copyOf(snapshot);
			} finally {
				lock.unlock();
			}
		}

		private void addTransaction(TransactionType type, BigDecimal amount, String description) {
			transactions.add(new Transaction(type, amount, description));
		}
	}

	private static final class WalletService {
		private final Map<String, WalletAccount> accounts = new ConcurrentHashMap<>();

		private void register(String mobileNumber, String name, BigDecimal initialBalance) {
			accounts.put(mobileNumber, new WalletAccount(mobileNumber, name, initialBalance));
		}

		private WalletAccount findAccount(String mobileNumber) throws WalletException {
			WalletAccount account = accounts.get(mobileNumber);
			if (account == null) {
				throw new WalletException("No wallet found for mobile number " + mobileNumber + ".");
			}
			return account;
		}

		private void cashIn(String mobileNumber, BigDecimal amount) throws WalletException {
			WalletAccount account = findAccount(mobileNumber);
			BigDecimal validAmount = validateAmount(amount);
			account.lock.lock();
			try {
				account.balance = account.balance.add(validAmount);
				account.addTransaction(TransactionType.CASH_IN, validAmount, "Simulated cash-in");
			} finally {
				account.lock.unlock();
			}
		}

		private void sendMoney(String senderNumber, String recipientNumber, BigDecimal amount) throws WalletException {
			if (senderNumber.equals(recipientNumber)) {
				throw new WalletException("You cannot send money to the same wallet.");
			}
			WalletAccount sender = findAccount(senderNumber);
			WalletAccount recipient = findAccount(recipientNumber);
			BigDecimal validAmount = validateAmount(amount);
			WalletAccount first = sender.mobileNumber.compareTo(recipient.mobileNumber) < 0 ? sender : recipient;
			WalletAccount second = first == sender ? recipient : sender;

			first.lock.lock();
			second.lock.lock();
			try {
				ensureSufficientBalance(sender, validAmount);
				sender.balance = sender.balance.subtract(validAmount);
				recipient.balance = recipient.balance.add(validAmount);
				sender.addTransaction(TransactionType.SEND_MONEY, validAmount,
						"Sent to " + recipient.name + " (" + recipient.mobileNumber + ")");
				recipient.addTransaction(TransactionType.RECEIVE_MONEY, validAmount,
						"Received from " + sender.name + " (" + sender.mobileNumber + ")");
			} finally {
				second.lock.unlock();
				first.lock.unlock();
			}
		}

		private void payBill(String mobileNumber, String biller, BigDecimal amount) throws WalletException {
			WalletAccount account = findAccount(mobileNumber);
			BigDecimal validAmount = validateAmount(amount);
			account.lock.lock();
			try {
				ensureSufficientBalance(account, validAmount);
				account.balance = account.balance.subtract(validAmount);
				account.addTransaction(TransactionType.BILL_PAYMENT, validAmount, "Bill payment to " + biller);
			} finally {
				account.lock.unlock();
			}
		}

		private void buyLoad(String mobileNumber, String recipientNumber, BigDecimal amount) throws WalletException {
			WalletAccount account = findAccount(mobileNumber);
			BigDecimal validAmount = validateAmount(amount);
			account.lock.lock();
			try {
				ensureSufficientBalance(account, validAmount);
				account.balance = account.balance.subtract(validAmount);
				account.addTransaction(TransactionType.MOBILE_LOAD, validAmount,
						"Mobile load for " + recipientNumber);
			} finally {
				account.lock.unlock();
			}
		}

		private static BigDecimal validateAmount(BigDecimal amount) throws WalletException {
			if (amount == null || amount.signum() <= 0) {
				throw new WalletException("Amount must be greater than zero.");
			}
			try {
				return amount.setScale(2, RoundingMode.UNNECESSARY);
			} catch (ArithmeticException e) {
				throw new WalletException("Amount cannot have more than two decimal places.");
			}
		}

		private static void ensureSufficientBalance(WalletAccount account, BigDecimal amount)
				throws WalletException {
			if (account.balance.compareTo(amount) < 0) {
				throw new WalletException("Insufficient wallet balance.");
			}
		}
	}

	private static final class WalletException extends Exception {
		private WalletException(String message) {
			super(message);
		}
	}
}
