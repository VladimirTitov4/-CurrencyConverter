package ru.titov.smartsoft.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import ru.titov.smartsoft.dto.ValcursDto;
import ru.titov.smartsoft.dto.ValuteDto;
import ru.titov.smartsoft.entity.ConvertedCurrency;
import ru.titov.smartsoft.entity.Currency;
import ru.titov.smartsoft.entity.Quote;
import ru.titov.smartsoft.entity.User;
import ru.titov.smartsoft.feign.CurrencyFeignClient;
import ru.titov.smartsoft.repository.ConvertedCurrencyRepository;
import ru.titov.smartsoft.repository.CurrencyRepository;
import ru.titov.smartsoft.repository.QuoteRepository;
import ru.titov.smartsoft.util.ConverterUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final QuoteRepository quoteRepository;
    private final ConvertedCurrencyRepository convertedCurrencyRepository;
    private final CurrencyFeignClient currencyFeignClient;

    public void getXmlAndSaveToDb(@AuthenticationPrincipal User user) {
        ValcursDto valcurs = currencyFeignClient.getXmlDaily();
        if (!checkIsUpToDate(valcurs)) {
            Quote quote = new Quote();
            quote.setName(valcurs.getName());
            quote.setDate(valcurs.getDate());
            quoteRepository.save(quote);
            Currency rubleCurrency = new Currency();
            rubleCurrency.setId(1L);
            rubleCurrency.setValuteId("R00000");
            rubleCurrency.setNumCode(1);
            rubleCurrency.setCharCode("RUB");
            rubleCurrency.setNominal(1);
            rubleCurrency.setName("Российский рубль");
            rubleCurrency.setValue(1.0);
            rubleCurrency.setUser(user);
            rubleCurrency.setQuote(quote);
            currencyRepository.save(rubleCurrency);
            for (ValuteDto valute : valcurs.getValutes()) {
                currencyRepository.save(ConverterUtil.toCurrencyEntity(valute, quote, user));
            }
        }
    }

    public boolean checkIsUpToDate(ValcursDto valcurs) {
        Quote lastQuote = quoteRepository.findFirstByOrderByIdDesc();
        if (null == lastQuote) {
            System.out.println("Found nothing, saving quotes for the first time");
            return false;
        }
        LocalDate DBDate = LocalDate.parse(lastQuote.getDate(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        LocalDate CBRDate = LocalDate.parse(valcurs.getDate(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        if (DBDate.equals(CBRDate)) {
            System.out.println("Date is up to Date");
            return true;
        }
        if (DBDate.isBefore(CBRDate)) {
            System.out.println("Date is expired, saving new quotes");
            return false;
        }
        return false;
    }

    public void saveConversion(ConvertedCurrency convertedCurrency) {
        convertedCurrencyRepository.save(convertedCurrency);
    }

    public List<Currency> loadRecentCurrencies() {
        Long lastId = quoteRepository.findFirstByOrderByIdDesc().getId();
        return currencyRepository.findAllByQuote_Id(lastId);
    }
}
