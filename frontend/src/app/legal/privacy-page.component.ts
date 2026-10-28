import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'lendly-privacy-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <article class="legal" aria-labelledby="privacy-title">
      <p class="page-eyebrow">Dokumenty</p>
      <h1 id="privacy-title" class="page-title">Polityka prywatności</h1>
      <p class="legal-date">Obowiązuje od 28&nbsp;października 2026&nbsp;r.</p>
      <p class="legal-lead">
        Lendly to niekomercyjny projekt demonstracyjny. Ta strona opisuje, jakie dane przetwarza serwis
        https://lendly.com.pl, w jakim celu i komu mogą trafić.
      </p>

      <h2>Administrator danych</h2>
      <p>
        Administratorem Twoich danych osobowych jest Konrad Malinowski, osoba fizyczna. W sprawach dotyczących danych
        i zgłoszeń pisz na adres <a href="mailto:kontakt@lendly.com.pl">kontakt&#64;lendly.com.pl</a>.
      </p>

      <h2>Jakie dane przetwarzamy i po co</h2>
      <ul>
        <li>
          <strong>Konto.</strong> Nazwa wyświetlana, adres e-mail i hasło. Hasła nie zapisujemy, tylko jego skrót.
          Możesz też podać nazwę swojej okolicy. Dane te służą do założenia konta, logowania i pokazania innym
          użytkownikom, z kim się umawiają.
        </li>
        <li>
          <strong>Logowanie i sesje.</strong> Identyfikator sesji, skróty tokenów oraz daty utworzenia i wygaśnięcia.
          W bazie sesji nie zapisujemy adresu IP ani nazwy przeglądarki.
        </li>
        <li>
          <strong>Potwierdzenie adresu e-mail.</strong> Wysyłamy na Twój adres sześciocyfrowy kod. W bazie przechowujemy
          tylko jego skrót, przez krótki czas.
        </li>
        <li>
          <strong>Weryfikacja dwuetapowa.</strong> Możesz ją włączyć dobrowolnie, a moderatorzy i administratorzy muszą.
          Zapisujemy zaszyfrowany sekret, skróty kodów odzyskiwania i liczbę błędnych prób.
        </li>
        <li>
          <strong>Logowanie przez Google.</strong> Tylko jeśli je wybierzesz. Zapisujemy identyfikator konta Google,
          adres e-mail z tego konta i informację, czy Google uznaje go za zweryfikowany.
        </li>
        <li>
          <strong>Ogłoszenia.</strong> Tytuł, opis, kategoria, stan, cena, zdjęcia i punkt odbioru. Publicznie pokazujemy
          tylko przybliżony punkt na mapie. Dokładny punkt odbioru widzi właściciel, a druga strona rezerwacji dopiero
          po jej zaakceptowaniu. Wgrane zdjęcia są zapisywane od nowa jako zwykłe pliki JPEG, więc tracą metadane
          z aparatu, także położenie GPS.
        </li>
        <li>
          <strong>Rezerwacje.</strong> Daty, status, cena orientacyjna, opcjonalna wiadomość (do 500 znaków) i powód
          decyzji. Widzą je obie strony rezerwacji, razem z Twoją nazwą wyświetlaną i okolicą.
        </li>
        <li>
          <strong>Zgłoszenia i moderacja.</strong> Powód i opis zgłoszenia (do 1000 znaków), decyzja moderatora oraz
          działania wobec ogłoszeń i kont. Zgłoszenia widzą moderatorzy.
        </li>
        <li>
          <strong>Podpowiedzi AI.</strong> Tylko wtedy, gdy uruchomisz funkcję Skan albo Misje. Szczegóły są
          w osobnej części poniżej.
        </li>
        <li>
          <strong>Przybliżona lokalizacja.</strong> Tylko jeśli klikniesz wyszukiwanie w pobliżu i zgodzisz się
          w przeglądarce. Zaokrąglamy ją do około 1 km i używamy do jednego wyszukiwania. Nie zapisujemy jej na koncie.
          Trafia jednak do adresu zapytania, więc może znaleźć się w dziennikach serwera WWW.
        </li>
        <li>
          <strong>Dane techniczne.</strong> Serwer WWW zapisuje standardowe dzienniki dostępu, m.in. adres IP, czas
          i adres żądania. Aplikacja zapisuje zdarzenia techniczne z identyfikatorami (na przykład ogłoszenia lub
          rezerwacji), bez adresów e-mail i adresów IP. Ograniczanie liczby żądań działa w pamięci serwera:
          adres IP służy tam tylko do wyliczenia licznika i nie jest zapisywany na dysku.
        </li>
      </ul>

      <h2>Podstawy prawne</h2>
      <ul>
        <li>
          <strong>Art. 6 ust. 1 lit. b RODO</strong>, czyli wykonanie umowy o korzystanie z serwisu: konto, logowanie,
          ogłoszenia, rezerwacje, kod z e-maila.
        </li>
        <li>
          <strong>Art. 6 ust. 1 lit. f RODO</strong>, czyli uzasadniony interes administratora: bezpieczeństwo serwisu,
          ochrona przed nadużyciami, ograniczanie liczby żądań, dzienniki serwera, moderacja zgłoszeń i obrona przed
          roszczeniami.
        </li>
        <li>
          <strong>Art. 6 ust. 1 lit. a RODO</strong>, czyli Twoja zgoda: tylko przy udostępnieniu przybliżonej
          lokalizacji przeglądarce. Zgodę wyrażasz w oknie przeglądarki i możesz ją cofnąć w jej ustawieniach.
        </li>
      </ul>
      <p>
        Podanie danych do konta jest dobrowolne, ale bez nich nie założymy konta. Nie podejmujemy wobec Ciebie decyzji
        w sposób wyłącznie zautomatyzowany. Podpowiedzi AI nie decydują o niczym za Ciebie.
      </p>

      <h2>Podpowiedzi AI: co wysyłamy i komu</h2>
      <p>
        Gdy użyjesz Skanu, wysyłamy do dostawcy AI wybrane przez Ciebie zdjęcie w postaci zapisanej od nowa jako JPEG
        (bez metadanych aparatu), razem z listą kategorii serwisu. Gdy użyjesz Misji, wysyłamy opisane przez Ciebie
        zadanie (do 500 znaków) i listę kategorii. Dostawcą jest Google (Gemini) albo OpenAI, zależnie od konfiguracji
        serwisu w danym momencie.
      </p>
      <p>
        Nie wysyłamy Twojej nazwy, adresu e-mail, identyfikatora konta ani lokalizacji. Lokalizacja i daty z Misji
        służą tylko do filtrowania wyników u nas. Nie zapisujemy w bazie zdjęcia ze Skanu ani treści zadania.
        Nie wpisuj w opisie zadania ani nie fotografuj danych osobowych swoich ani cudzych. Jak długo dostawca AI
        przechowuje przesłane dane, zależy od jego zasad.
      </p>

      <h2>Odbiorcy danych</h2>
      <ul>
        <li>
          <strong>Dostawca hostingu</strong>, czyli serwery, na których działa serwis. Tam znajdują się baza danych,
          zdjęcia i kopie zapasowe.
        </li>
        <li>
          <strong>Dostawca AI</strong> (Google lub OpenAI), tylko gdy użyjesz Skanu albo Misji.
        </li>
        <li>
          <strong>OpenFreeMap</strong> (tiles.openfreemap.org), który dostarcza kafelki map. Przeglądarka łączy się
          z nim bezpośrednio, więc dostawca widzi Twój adres IP i oglądany obszar.
        </li>
        <li>
          <strong>Dostawca poczty</strong>, który wysyła wiadomość z kodem potwierdzającym (gdy poczta jest
          skonfigurowana). Dostaje Twój adres e-mail i treść wiadomości.
        </li>
        <li>
          <strong>Google</strong>, tylko jeśli wybierzesz logowanie przez Google.
        </li>
        <li>
          <strong>Inni użytkownicy.</strong> Publicznie widać nazwę wyświetlaną, okolicę, datę rejestracji i Twoje
          aktywne ogłoszenia. Druga strona rezerwacji widzi dane z rezerwacji opisane wyżej.
        </li>
      </ul>
      <p>
        Dostawcy AI i map mogą przetwarzać dane poza Europejskim Obszarem Gospodarczym, w tym w USA.
        Opieramy się wtedy na mechanizmach ochrony, które stosuje dany dostawca. Nie sprzedajemy danych i nie używamy
        ich do reklam.
      </p>

      <h2>Jak długo przechowujemy dane</h2>
      <ul>
        <li>Dane konta, ogłoszenia i rezerwacje: dopóki masz konto albo dopóki nie poprosisz o usunięcie.</li>
        <li>Sesje: najwyżej 30 dni od zalogowania, token dostępu działa 10 minut.</li>
        <li>Kody z e-maila: ważne krótko, wygasłe usuwamy po dobie. Wyzwania weryfikacji dwuetapowej usuwamy po godzinie od wygaśnięcia.</li>
        <li>
          Kopie zapasowe: codziennie powstaje kopia bazy i zdjęć, a przechowujemy 14 ostatnich. Usunięte dane mogą więc
          zostać w kopiach jeszcze do około dwóch tygodni.
        </li>
        <li>
          Zgłoszenia i decyzje moderacji: tak długo, jak są potrzebne do obsługi zgłoszenia i obrony przed roszczeniami.
        </li>
        <li>Dzienniki serwera WWW: przez ograniczony czas potrzebny do zapewnienia bezpieczeństwa i diagnostyki.</li>
      </ul>

      <h2>Twoje prawa</h2>
      <p>Masz prawo do:</p>
      <ul>
        <li>dostępu do swoich danych i otrzymania ich kopii,</li>
        <li>sprostowania danych,</li>
        <li>usunięcia danych,</li>
        <li>ograniczenia przetwarzania,</li>
        <li>przenoszenia danych,</li>
        <li>sprzeciwu wobec przetwarzania opartego na uzasadnionym interesie,</li>
        <li>cofnięcia zgody w każdej chwili, bez wpływu na wcześniejsze przetwarzanie.</li>
      </ul>
      <p>
        Nazwę wyświetlaną i okolicę zmienisz w <a routerLink="/profile">profilu</a>. Samodzielnego usunięcia konta
        w serwisie jeszcze nie ma. Aby usunąć konto lub skorzystać z pozostałych praw, napisz na
        <a href="mailto:kontakt@lendly.com.pl">kontakt&#64;lendly.com.pl</a>. Możesz też złożyć skargę do Prezesa Urzędu
        Ochrony Danych Osobowych (<a href="https://uodo.gov.pl" rel="noopener">uodo.gov.pl</a>).
      </p>

      <h2>Pliki cookie i pamięć przeglądarki</h2>
      <p>
        Używamy wyłącznie plików i wpisów niezbędnych do działania serwisu. Nie ma u nas analityki, reklam ani
        śledzenia, dlatego nie pokazujemy banera ze zgodą.
      </p>
      <ul>
        <li>
          <strong>Cookie logowania</strong> (lendly_access, lendly_refresh): utrzymują sesję po zalogowaniu.
          Są niedostępne dla skryptów na stronie (HttpOnly).
        </li>
        <li>
          <strong>Cookie ochrony formularzy</strong> (lendly_csrf_context): chroni logowanie, rejestrację i zmiany
          danych przed podszyciem się pod Ciebie. Ważne do 30 minut.
        </li>
        <li>
          <strong>Cookie drugiego kroku logowania</strong> (lendly_mfa): ustawiane na 5 minut, gdy masz włączoną
          weryfikację dwuetapową.
        </li>
        <li>
          <strong>Pamięć lokalna</strong> (lendly-theme): zapamiętuje wybrany jasny lub ciemny motyw.
        </li>
        <li>
          <strong>Pamięć podręczna aplikacji</strong> (service worker): przechowuje pliki aplikacji, żeby szybciej
          się ładowała i pokazała komunikat, gdy brakuje sieci.
        </li>
      </ul>
      <p>
        Na serwerach, z którymi łączy się Twoja przeglądarka (OpenFreeMap, a przy logowaniu Google),
        mogą obowiązywać ich własne zasady dotyczące plików cookie i danych.
      </p>

      <h2>Bezpieczeństwo</h2>
      <p>
        Hasła zapisujemy jako skróty, a sekrety weryfikacji dwuetapowej w postaci zaszyfrowanej. Tokeny sesji nie są
        dostępne dla skryptów na stronie. Dokładny punkt odbioru nie jest publiczny, a zdjęcia tracą metadane aparatu.
        Ograniczamy liczbę prób logowania i innych żądań. Żaden serwis nie daje pełnej gwarancji, więc o podejrzeniu
        wycieku lub przejęcia konta napisz na
        <a href="mailto:kontakt@lendly.com.pl">kontakt&#64;lendly.com.pl</a>.
      </p>

      <h2>Zmiany polityki</h2>
      <p>
        Gdy zmienimy sposób przetwarzania danych, zaktualizujemy tę stronę i datę na jej początku. Zasady korzystania
        z serwisu opisuje <a routerLink="/terms">Regulamin</a>.
      </p>
    </article>
  `,
  styleUrl: './legal-page.scss'
})
export class PrivacyPageComponent {}
