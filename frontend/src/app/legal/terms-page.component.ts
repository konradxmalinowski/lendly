import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'lendly-terms-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <article class="legal" aria-labelledby="terms-title">
      <p class="page-eyebrow">Dokumenty</p>
      <h1 id="terms-title" class="page-title">Regulamin</h1>
      <p class="legal-date">Obowiązuje od 28&nbsp;października 2026&nbsp;r.</p>
      <p class="legal-lead">
        Ten regulamin określa zasady korzystania z serwisu https://lendly.com.pl. Zakładając konto, akceptujesz go.
      </p>

      <h2>Czym jest Lendly</h2>
      <p>
        Lendly to miejsce, w którym sąsiedzi umawiają się na pożyczanie sobie rzeczy. Serwis prowadzi Konrad Malinowski,
        osoba fizyczna, jako niekomercyjny projekt demonstracyjny. Korzystanie z serwisu jest bezpłatne.
      </p>
      <p>
        Lendly pomaga znaleźć rzecz i umówić termin. Nie jest stroną umowy między właścicielem a pożyczającym i nie
        przyjmuje żadnych płatności. Cena w ogłoszeniu jest informacją, a rozliczenie odbywa się poza serwisem.
      </p>

      <h2>Konto</h2>
      <ul>
        <li>Podajesz prawdziwą nazwę i własny adres e-mail, który potwierdzasz kodem.</li>
        <li>Nie udostępniasz nikomu hasła i odpowiadasz za działania na swoim koncie.</li>
        <li>O podejrzeniu przejęcia konta napisz na <a href="mailto:kontakt@lendly.com.pl">kontakt&#64;lendly.com.pl</a>.</li>
        <li>Rejestracja może być czasowo wyłączona.</li>
        <li>Konto możesz usunąć, pisząc na adres kontaktowy. Zasady dotyczące danych opisuje <a routerLink="/privacy">Polityka prywatności</a>.</li>
      </ul>

      <h2>Ogłoszenia</h2>
      <ul>
        <li>Dodajesz tylko rzeczy, które do Ciebie należą albo które masz prawo komuś pożyczyć.</li>
        <li>Tytuł, opis, stan i cena mają być zgodne z prawdą. Zdjęcia pokazują tę rzecz, którą pożyczasz.</li>
        <li>Nie publikuj zdjęć, na których widać inne osoby, bez ich zgody.</li>
        <li>
          Zabronione są ogłoszenia o rzeczach, których posiadanie lub udostępnianie jest niezgodne z prawem, w tym
          broni i amunicji, materiałów wybuchowych, środków odurzających oraz rzeczy pochodzących z przestępstwa.
          Zabronione są też oszustwa, spam i treści naruszające prawa innych osób.
        </li>
        <li>
          Treści, które dodajesz, zostają Twoje. Dajesz serwisowi prawo do ich wyświetlania w serwisie w zakresie
          potrzebnym do jego działania.
        </li>
      </ul>

      <h2>Rezerwacje</h2>
      <ul>
        <li>Pożyczający wysyła prośbę o rezerwację z wybranym terminem (do 30 dni) i może dodać krótką wiadomość.</li>
        <li>Właściciel akceptuje prośbę albo ją odrzuca. Prośba bez odpowiedzi wygasa.</li>
        <li>Obie strony mogą anulować rezerwację. Właściciel podaje wtedy powód.</li>
        <li>Przekazanie i zwrot rzeczy oznacza w serwisie właściciel.</li>
        <li>Właściciel może zablokować terminy, w których rzecz jest niedostępna.</li>
      </ul>

      <h2>Przekazanie i zwrot</h2>
      <p>
        Przekazanie i zwrot rzeczy odbywają się wyłącznie między użytkownikami. To oni ustalają miejsce i godzinę,
        sprawdzają stan rzeczy, a jeśli chcą, także opłatę, kaucję i odpowiedzialność za uszkodzenia. Lendly nie
        sprawdza tożsamości użytkowników ani rzeczy i nie ubezpiecza pożyczania. Warto spotykać się w miejscu
        publicznym i opisać stan rzeczy przy przekazaniu.
      </p>

      <h2>Podpowiedzi AI</h2>
      <p>
        Skan i Misje podpowiadają treść ogłoszenia albo rzeczy potrzebne do zadania. To tylko wskazówki i mogą być
        błędne. Przed publikacją sprawdź i popraw wszystko, bo za treść ogłoszenia odpowiadasz Ty. Cenę, stan i punkt
        odbioru zawsze podajesz Ty. Uwagi o bezpieczeństwie w Misjach nie zastępują fachowca. Co trafia do dostawcy AI,
        opisuje <a routerLink="/privacy">Polityka prywatności</a>.
      </p>

      <h2>Moderacja i zawieszenie konta</h2>
      <p>
        Każdy zalogowany użytkownik może zgłosić ogłoszenie lub konto. Moderatorzy rozpatrują zgłoszenia i mogą ukryć
        ogłoszenie albo zawiesić konto, gdy naruszono Regulamin lub prawo albo gdy jest uzasadnione podejrzenie
        nadużycia. Jeśli uważasz, że decyzja była błędna, napisz na
        <a href="mailto:kontakt@lendly.com.pl">kontakt&#64;lendly.com.pl</a>. Sprawdzimy ją ponownie.
      </p>

      <h2>Odpowiedzialność</h2>
      <p>
        Serwis działa nieodpłatnie i w takim stanie, w jakim jest. Może być czasowo niedostępny, zmieniony albo
        wyłączony. Nie odpowiadamy za zachowanie użytkowników, stan pożyczanych rzeczy, szkody powstałe przy ich
        pożyczaniu ani za treści dodane przez użytkowników. Odpowiedzialność za szkodę między właścicielem
        a pożyczającym ocenia się według przepisów prawa.
      </p>
      <p>
        Żaden zapis Regulaminu nie wyłącza ani nie ogranicza odpowiedzialności, której nie można wyłączyć ani
        ograniczyć na mocy prawa, w szczególności wobec konsumentów i za szkody wyrządzone umyślnie.
      </p>

      <h2>Reklamacje i kontakt</h2>
      <p>
        Uwagi, reklamacje i zgłoszenia nadużyć wysyłaj na
        <a href="mailto:kontakt@lendly.com.pl">kontakt&#64;lendly.com.pl</a>. Odpowiemy bez zbędnej zwłoki. Konsument
        może też skorzystać z pozasądowych sposobów rozpatrywania sporów.
      </p>

      <h2>Prawo właściwe</h2>
      <p>
        Do Regulaminu stosuje się prawo polskie. Spory rozstrzygają sądy właściwe według przepisów ogólnych.
        Przepisy chroniące konsumentów pozostają bez zmian.
      </p>

      <h2>Zmiany regulaminu</h2>
      <p>
        Nową wersję opublikujemy na tej stronie z datą, od której obowiązuje. O istotnej zmianie poinformujemy
        w serwisie. Jeśli nie zgadzasz się z nową wersją, możesz poprosić o usunięcie konta.
      </p>
    </article>
  `,
  styleUrl: './legal-page.scss'
})
export class TermsPageComponent {}
