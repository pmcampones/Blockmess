package demo.cryptocurrency.client;


public class InsufficientFundsException extends Exception {

	private static final String DEFAULT_MESSAGE = "Cannot issue a transaction with amount %d because my funds are %d";

	public InsufficientFundsException(int txAmount, int myFunds) {
		super(String.format(DEFAULT_MESSAGE, txAmount, myFunds));
	}

}
