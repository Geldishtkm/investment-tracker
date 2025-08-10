// Coin mapping utility to convert user input to proper CoinGecko API coin IDs
export interface CoinMapping {
  id: string;           // CoinGecko API ID (e.g., "bitcoin")
  name: string;         // Full name (e.g., "Bitcoin")
  symbol: string;       // Symbol (e.g., "BTC")
  aliases: string[];    // Common variations and abbreviations
}

// Comprehensive coin mapping database
export const COIN_MAPPINGS: CoinMapping[] = [
  {
    id: "bitcoin",
    name: "Bitcoin",
    symbol: "BTC",
    aliases: ["btc", "bitcoin", "xbt"]
  },
  {
    id: "ethereum",
    name: "Ethereum", 
    symbol: "ETH",
    aliases: ["eth", "ethereum", "ether"]
  },
  {
    id: "solana",
    name: "Solana",
    symbol: "SOL", 
    aliases: ["sol", "solana"]
  },
  {
    id: "cardano",
    name: "Cardano",
    symbol: "ADA",
    aliases: ["ada", "cardano"]
  },
  {
    id: "polkadot",
    name: "Polkadot",
    symbol: "DOT",
    aliases: ["dot", "polkadot"]
  },
  {
    id: "ripple",
    name: "Ripple",
    symbol: "XRP",
    aliases: ["xrp", "ripple"]
  },
  {
    id: "binancecoin",
    name: "BNB",
    symbol: "BNB",
    aliases: ["bnb", "binance", "binance coin"]
  },
  {
    id: "dogecoin",
    name: "Dogecoin",
    symbol: "DOGE",
    aliases: ["doge", "dogecoin"]
  },
  {
    id: "avalanche-2",
    name: "Avalanche",
    symbol: "AVAX",
    aliases: ["avax", "avalanche"]
  },
  {
    id: "chainlink",
    name: "Chainlink",
    symbol: "LINK",
    aliases: ["link", "chainlink"]
  },
  {
    id: "polygon",
    name: "Polygon",
    symbol: "MATIC",
    aliases: ["matic", "polygon"]
  },
  {
    id: "uniswap",
    name: "Uniswap",
    symbol: "UNI",
    aliases: ["uni", "uniswap"]
  },
  {
    id: "litecoin",
    name: "Litecoin",
    symbol: "LTC",
    aliases: ["ltc", "litecoin"]
  },
  {
    id: "stellar",
    name: "Stellar",
    symbol: "XLM",
    aliases: ["xlm", "stellar"]
  },
  {
    id: "vechain",
    name: "VeChain",
    symbol: "VET",
    aliases: ["vet", "vechain"]
  },
  {
    id: "filecoin",
    name: "Filecoin",
    symbol: "FIL",
    aliases: ["fil", "filecoin"]
  },
  {
    id: "cosmos",
    name: "Cosmos",
    symbol: "ATOM",
    aliases: ["atom", "cosmos"]
  },
  {
    id: "monero",
    name: "Monero",
    symbol: "XMR",
    aliases: ["xmr", "monero"]
  },
  {
    id: "algorand",
    name: "Algorand",
    symbol: "ALGO",
    aliases: ["algo", "algorand"]
  },
  {
    id: "tezos",
    name: "Tezos",
    symbol: "XTZ",
    aliases: ["xtz", "tezos"]
  }
];

/**
 * Find the proper CoinGecko coin ID from user input
 * @param userInput - User's input (can be name, symbol, or alias)
 * @returns CoinGecko API coin ID or null if not found
 */
export function findCoinId(userInput: string): string | null {
  if (!userInput) return null;
  
  const input = userInput.toLowerCase().trim();
  
  // First, try exact match with ID
  const exactMatch = COIN_MAPPINGS.find(coin => coin.id === input);
  if (exactMatch) return exactMatch.id;
  
  // Then try exact match with symbol
  const symbolMatch = COIN_MAPPINGS.find(coin => coin.symbol.toLowerCase() === input);
  if (symbolMatch) return symbolMatch.id;
  
  // Then try exact match with name
  const nameMatch = COIN_MAPPINGS.find(coin => coin.name.toLowerCase() === input);
  if (nameMatch) return nameMatch.id;
  
  // Finally, try partial matches with aliases
  const aliasMatch = COIN_MAPPINGS.find(coin => 
    coin.aliases.some(alias => alias.toLowerCase() === input)
  );
  if (aliasMatch) return aliasMatch.id;
  
  // If no exact match, try partial matches
  const partialMatch = COIN_MAPPINGS.find(coin => 
    coin.name.toLowerCase().includes(input) ||
    coin.symbol.toLowerCase().includes(input) ||
    coin.aliases.some(alias => alias.toLowerCase().includes(input))
  );
  
  return partialMatch ? partialMatch.id : null;
}

/**
 * Get coin information by ID
 * @param coinId - CoinGecko API coin ID
 * @returns Coin mapping information or null if not found
 */
export function getCoinInfo(coinId: string): CoinMapping | null {
  return COIN_MAPPINGS.find(coin => coin.id === coinId) || null;
}

/**
 * Get all available coins for autocomplete
 * @returns Array of coin names and symbols
 */
export function getAvailableCoins(): Array<{id: string, name: string, symbol: string}> {
  return COIN_MAPPINGS.map(coin => ({
    id: coin.id,
    name: coin.name,
    symbol: coin.symbol
  }));
}

/**
 * Check if a string represents a crypto asset
 * @param input - String to check
 * @returns True if it's a recognized crypto asset
 */
export function isCryptoAsset(input: string): boolean {
  return findCoinId(input) !== null;
} 