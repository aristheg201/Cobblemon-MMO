package vn.svframe.fantasyhub.core;

import java.util.function.Predicate;
import vn.svframe.svframelib.api.economy.CurrencyKey;
import vn.svframe.svframelib.api.economy.CurrencyService;

/** Resolve the BeastCoin role to its registered ID, never to another wallet on insufficient funds. */
final class CommerceCurrencies {
 static String beastId(Predicate<String> registered){return registered.test("beastcoin")?"beastcoin":registered.test("secondary")?"secondary":"beastcoin";}
 static CurrencyKey beast(){var service=CurrencyService.get();return CurrencyKey.beconomy(beastId(id->service.isAvailable(CurrencyKey.beconomy(id))));}
 static CurrencyKey payment(int method){CommercePolicy.currency(method);return method==1?beast():CurrencyKey.beconomyPrimary();}
}
