package org.cryptocoinpartners.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.OrderBuilder;
import org.cryptocoinpartners.schema.OrderBuilder.SpecificOrderBuilder;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.PortfolioServiceException;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.Remainder;
import org.slf4j.Logger;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Singleton
public class BasicPortfolioService implements PortfolioService {

	private final Portfolio portfolio;
	private final ConcurrentHashMap<Asset, Amount> allPnLs;

	public BasicPortfolioService(Portfolio portfolio) {
		this.portfolio = portfolio;
		this.allPnLs = new ConcurrentHashMap<Asset, Amount>();

	}

	@Override
	@Nullable
	public ArrayList<Position> getPositions() {
		return (ArrayList<Position>) portfolio.getPositions();
	}

	@Override
	@Nullable
	public ConcurrentHashMap<Asset, Amount> getRealisedPnLs() {
		return portfolio.getRealisedPnLs();
	}

	@Override
	@Nullable
	public Amount getRealisedPnL(Asset asset) {
		return portfolio.getRealisedPnL(asset);
	}

	@Override
	@Nullable
	public ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Market, Amount>>> getRealisedPnLByMarket() {
		return portfolio.getRealisedPnL();
	}

	public long getLongPosition(Asset asset, Exchange exchange) {
		return portfolio.getLongPosition(asset, exchange);
	}

	public long getShortPosition(Asset asset, Exchange exchange) {
		return portfolio.getShortPosition(asset, exchange);
	}

	public DiscreteAmount getNetPosition(Asset asset, Exchange exchange) {
		return portfolio.getNetPosition(asset, exchange);
	}

	@Override
	@Nullable
	public ArrayList<Position> getPositions(Exchange exchange) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	@Nullable
	public Collection<Position> getPositions(Asset asset, Exchange exchange) {
		return portfolio.getPositions(asset, exchange);

	}

	@Override
	public DiscreteAmount getLastTrade() {

		List<Object> events = null;
		try {
			events = context.loadStatementByName("GET_LAST_TICK");
			if (events.size() > 0) {
				Trade trade = ((Trade) events.get(events.size() - 1));
				return (trade.getPrice());

			}
		} catch (ParseException | DeploymentException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	@Transient
	public ConcurrentHashMap<Asset, Amount> getCashBalances() {

		// sum of all transactions that belongs to this strategy
		//BigDecimal balance = BigDecimal.ZERO;
		//DiscreteAmount(0, 0.01);
		//Amount balance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());
		Amount balance = DecimalAmount.ZERO;
		//DecimalAmount.ZERO;

		//= DecimalAmount.ZERO;
		ConcurrentHashMap<Asset, Amount> balances = new ConcurrentHashMap<>();
		Iterator<Transaction> itt = getTrades().iterator();
		while (itt.hasNext()) {
			Transaction transaction = itt.next();
			if (balances.get(transaction.getAsset()) != null) {

				balance = balances.get(transaction.getAsset());

			}
			Amount tranCost = transaction.getCost();
			balance = balance.plus(tranCost);
			balances.put(transaction.getAsset(), balance);

		}

		// plus part of all cashFlows
		Amount cashFlows = DecimalAmount.ZERO;
		//Amount cashFlows = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

		Iterator<Transaction> itc = getCashFlows().iterator();
		while (itc.hasNext()) {
			Transaction cashFlowTransaction = itc.next();
			if (balances.get(cashFlowTransaction.getCurrency()) != null) {

				cashFlows = balances.get(cashFlowTransaction.getCurrency());
			}
			Amount tranCost = cashFlowTransaction.getCost();
			cashFlows = cashFlows.plus(tranCost);
			balances.put(cashFlowTransaction.getCurrency(), cashFlows);

		}
		//Amount amount = balance.plus(cashFlows);

		Iterator<Asset> it = getRealisedPnLByMarket().keySet().iterator();
		while (it.hasNext()) {
			Asset asset = it.next();
			Iterator<Exchange> ite = getRealisedPnLByMarket().get(asset).keySet().iterator();
			while (ite.hasNext()) {
				Exchange exchange = ite.next();
				Iterator<Market> itm = getRealisedPnLByMarket().get(asset).get(exchange).keySet().iterator();
				while (itm.hasNext()) {
					Market market = itm.next();

					Amount realisedPnL = getRealisedPnLByMarket().get(asset).get(exchange).get(market);
					if (exchange.getMargin() != 1 && !realisedPnL.isZero()) {
						if (balances.get(asset) != null) {

							balance = balances.get(asset);

						}
						balance = balance.plus(realisedPnL);
						balances.put(asset, balance);

					}

				}
			}

		}

		return balances;
	}

	@Override
	@Transient
	@SuppressWarnings("null")
	public List<Transaction> getCashFlows() {
		// return all CREDIT,DEBIT,INTREST,FEES and REALISED PnL

		ArrayList<Transaction> cashFlows = new ArrayList<>();
		Iterator<Transaction> it = portfolio.getTransactions().iterator();
		while (it.hasNext()) {
			Transaction transaction = it.next();
			if (transaction.getType() == TransactionType.CREDIT || transaction.getType() == TransactionType.DEBIT
					|| transaction.getType() == TransactionType.INTREST || transaction.getType() == TransactionType.FEES
					|| transaction.getType() == TransactionType.REALISED_PROFIT_LOSS) {
				cashFlows.add(transaction);
			}
		}

		return cashFlows;
	}

	@Override
	@Transient
	@SuppressWarnings("null")
	public List<Transaction> getTrades() {
		//return all BUY and SELL
		ArrayList<Transaction> trades = new ArrayList<>();
		Iterator<Transaction> it = portfolio.getTransactions().iterator();
		while (it.hasNext()) {
			Transaction transaction = it.next();

			if (transaction.getType() == TransactionType.BUY || transaction.getType() == TransactionType.SELL) {
				trades.add(transaction);
			}
		}
		return trades;
	}

	@Override
	@Transient
	public DiscreteAmount getMarketPrice(Position postion) {

		if (postion.isOpen()) {
			if (postion.isShort()) {
				@SuppressWarnings("ConstantConditions")
				DiscreteAmount price = quotes.getLastAskForMarket(postion.getMarket()).getPrice();
				return price;

			} else {
				@SuppressWarnings("ConstantConditions")
				DiscreteAmount price = quotes.getLastBidForMarket(postion.getMarket()).getPrice();
				return price;
			}
		} else {
			return new DiscreteAmount(0, postion.getMarket().getVolumeBasis());

		}
	}

	@Override
	@Transient
	public Amount getMarketValue(Position postion) {

		if (postion.isOpen()) {

			return postion.getVolume().times(getMarketPrice(postion), Remainder.ROUND_EVEN);

		} else {
			return new DiscreteAmount(0, postion.getMarket().getVolumeBasis());

		}
	}

	@Override
	@Transient
	public Amount getUnrealisedPnL(Position postion) {

		if (postion.isLong()) {

			return (getMarketPrice(postion).minus(postion.getLongAvgPrice())).times(postion.getVolume(), Remainder.ROUND_EVEN);

		} else if (postion.isShort()) {
			return (getMarketPrice(postion).minus(postion.getShortAvgPrice())).times(postion.getVolume(), Remainder.ROUND_EVEN);
		}

		else {
			return new DiscreteAmount(0, postion.getMarket().getVolumeBasis());

		}
	}

	@Override
	@Transient
	public ConcurrentHashMap<Asset, Amount> getMarketValues() {
		Amount marketValue = DecimalAmount.ZERO;

		//Amount marketValue = new DiscreteAmount(0, 0.01);
		ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//portfolio.getPositions().keySet()
		Iterator<Position> it = portfolio.getPositions().iterator();
		while (it.hasNext()) {
			Position position = it.next();

			if (position.isOpen()) {
				if (marketValues.get(position.getAsset()) != null) {
					marketValue = marketValues.get(position.getAsset());
				}
				marketValue = marketValue.plus(getMarketValue(position));

				marketValues.put(position.getMarket().getQuote(), marketValue);

			}
		}

		return marketValues;

	}

	@Override
	@Transient
	public ConcurrentHashMap<Asset, Amount> getUnrealisedPnLs() {
		Amount unrealisedPnL = DecimalAmount.ZERO;

		//Amount marketValue = new DiscreteAmount(0, 0.01);
		ConcurrentHashMap<Asset, Amount> unrealisedPnLs = new ConcurrentHashMap<>();
		//portfolio.getPositions().keySet()
		Iterator<Position> it = portfolio.getPositions().iterator();
		while (it.hasNext()) {
			Position position = it.next();

			if (position.isOpen()) {
				if (unrealisedPnLs.get(position.getAsset()) != null) {
					unrealisedPnL = unrealisedPnLs.get(position.getAsset());
				}
				unrealisedPnL = unrealisedPnL.plus(getUnrealisedPnL(position));

				unrealisedPnLs.put(position.getMarket().getQuote(), unrealisedPnL);

			}
		}

		return unrealisedPnLs;

	}

	@Override
	@Transient
	@Inject
	public Amount getMarketValue() {
		//Amount marketValue;
		//ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//portfolio.get
		Asset quoteAsset = portfolio.getBaseAsset();
		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		//	Amount baseMarketValue = new DiscreteAmount(0, 0.01);
		Amount baseMarketValue = DecimalAmount.ZERO;

		ConcurrentHashMap<Asset, Amount> marketValues = getMarketValues();

		Iterator<Asset> it = marketValues.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			baseMarketValue = baseMarketValue.plus(marketValues.get(baseAsset).times(rate.getPrice(), Remainder.ROUND_EVEN));

		}

		return baseMarketValue;

	}

	@Override
	@Transient
	@Inject
	public Amount getUnrealisedPnL() {
		//Amount marketValue;
		//ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//portfolio.get
		Asset quoteAsset = portfolio.getBaseAsset();
		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		//	Amount baseMarketValue = new DiscreteAmount(0, 0.01);
		Amount baseUnrealisedPnL = DecimalAmount.ZERO;

		ConcurrentHashMap<Asset, Amount> unrealisedPnLs = getUnrealisedPnLs();

		Iterator<Asset> it = unrealisedPnLs.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			baseUnrealisedPnL = baseUnrealisedPnL.plus(unrealisedPnLs.get(baseAsset).times(rate.getPrice(), Remainder.ROUND_EVEN));

		}

		return baseUnrealisedPnL;

	}

	@Override
	@Transient
	public Amount getRealisedPnL() {

		//Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
		Asset quoteAsset = portfolio.getBaseAsset();
		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		Amount baseRealisedPnL = DecimalAmount.ZERO;

		//Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

		ConcurrentHashMap<Asset, Amount> realisedPnLs = getRealisedPnLs();

		Iterator<Asset> itp = realisedPnLs.keySet().iterator();
		while (itp.hasNext()) {
			Asset baseAsset = itp.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			Amount localPnL = realisedPnLs.get(baseAsset);
			Amount basePnL = localPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);
			baseRealisedPnL = baseRealisedPnL.plus(basePnL);

		}
		return baseRealisedPnL;
	}

	@Override
	@Transient
	public Amount getCashBalance() {
		//Amount marketValue;
		//ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
		Asset quoteAsset = portfolio.getBaseAsset();
		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		Amount baseCashBalance = DecimalAmount.ZERO;

		//Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

		ConcurrentHashMap<Asset, Amount> cashBalances = getCashBalances();

		Iterator<Asset> it = cashBalances.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			Amount localBalance = cashBalances.get(baseAsset);
			Amount baseBalance = localBalance.times(rate.getPrice(), Remainder.ROUND_EVEN);
			baseCashBalance = baseCashBalance.plus(baseBalance);

		}

		return baseCashBalance;

	}

	@Override
	public void CreateTransaction(Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price) {
		Transaction transaction = new Transaction(portfolio, exchange, asset, type, amount, price);
		context.publish(transaction);
	}

	@Override
	public void exitPosition(Position position) throws Exception {

		reducePosition(position, (position.getVolume().abs()));
	}

	@Override
	public void reducePosition(final Position position, final Amount amount) {
		try {
			this.handleReducePosition(position, amount);
		} catch (Throwable th) {
			throw new PortfolioServiceException("Error performing 'PositionService.reducePosition(int positionId, long quantity)' --> " + th, th);
		}
	}

	@Override
	public void handleReducePosition(Position position, Amount amount) throws Exception {

		Market market = position.getMarket();
		OrderBuilder orderBuilder = new OrderBuilder(position.getPortfolio(), orderService);
		if (orderBuilder != null) {
			SpecificOrderBuilder exitOrder = orderBuilder.create(context.getTime(), market, amount.negate(), "Exit Order");
			log.info("Entering trade with order " + exitOrder);
			orderService.placeOrder(exitOrder.getOrder());
		}

		if (!position.isOpen()) {
			//TODO remove subsrcption
		}
	}

	@Override
	public void handleSetExitPrice(Position position, Amount exitPrice, boolean force) throws PortfolioServiceException {

		// there needs to be a position
		if (position == null) {
			throw new PortfolioServiceException("position does not exist: ");
		}
		if (!force && (position.getLongExitPrice() == null || position.getShortExitPrice() == null)) {
			log.warn("no exit value was set for position: " + position);
			return;
		}

		// we don't want to set the exitValue to Zero
		if (exitPrice.isZero()) {
			log.warn("setting of exit Pirice of zero is prohibited: " + exitPrice);
			return;
		}

		if (!force) {
			if (position.isShort() && exitPrice.compareTo(position.getShortExitPrice()) > 0) {
				log.warn("exit value " + exitPrice + " is higher than existing exit value " + position.getShortExitPrice() + " of short position " + position);
				return;
			} else if (position.isLong() && exitPrice.compareTo(position.getLongExitPrice()) < 0) {
				log.warn("exit value " + exitPrice + " is lower than existing exit value " + position.getLongExitPrice() + " of long position " + position);
				return;
			}
		}

		// exitValue cannot be lower than currentValue
		Amount currentPrice = getMarketPrice(position);

		if (position.isShort() && exitPrice.compareTo(currentPrice) < 0) {
			throw new PortfolioServiceException("ExitValue (" + exitPrice + ") for short-position " + position + " is lower than currentValue: " + exitPrice);
		} else if (position.isLong() && exitPrice.compareTo(currentPrice) > 0) {
			throw new PortfolioServiceException("ExitValue (" + exitPrice + ") for long-position " + position + " is higher than currentValue: " + currentPrice);
		}

		//position.setExitPrice(exitPrice);

		log.info("set exit value " + position + " to " + exitPrice);
	}

	@Override
	public void handleSetMargin(Position position) throws Exception {
		//TODO manage setting and mainuplating margin

	}

	@Override
	public void handleSetMargins() throws Exception {
		//TODO manage setting and mainuplating margin

	}

	@Inject
	protected Context context;
	@Inject
	protected QuoteService quotes;

	@Inject
	protected OrderService orderService;
	@Inject
	private Logger log;

}
