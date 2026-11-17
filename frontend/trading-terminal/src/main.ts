import { Component, Injectable, OnInit, inject } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

interface Instrument { id: number; symbol: string; commodity: string; currency: string; unit: string; }
interface Ack { orderId: string; status: string; receivedAt: string; }

@Injectable({ providedIn: 'root' })
class TradingApi {
  private http = inject(HttpClient);
  instruments() { return firstValueFrom(this.http.get<Instrument[]>('/api/v1/instruments')); }
  submit(payload: object) { return firstValueFrom(this.http.post<Ack>('/api/v1/orders', payload)); }
}

@Component({
  selector: 'commoditycx-app',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main>
      <header><div><p class="eyebrow">MULTI-MARKET COMMODITIES PLATFORM</p><h1>Commodity<span>CX</span></h1></div><span class="environment">FOUNDATION / DEMO</span></header>
      <section class="intro"><div><p class="eyebrow">TRADING WORKSPACE</p><h2>Markets, orders & bilateral flow</h2><p class="muted">A starter interface for OTC RFQ and order-book workflows.</p></div></section>
      <section class="grid">
        <article class="panel"><p class="eyebrow">INSTRUMENTS</p><h3>Available markets</h3>
          @if (instruments.length) { @for (item of instruments; track item.id) {
            <div class="instrument"><div><strong>{{item.commodity}}</strong><small>{{item.symbol}}</small></div><span>{{item.currency}} / {{item.unit}}</span></div>
          }} @else { <p class="muted">{{message}}</p> }
        </article>
        <article class="panel"><p class="eyebrow">ORDER ENTRY</p><h3>Submit a test order</h3>
          <label>Instrument<select [(ngModel)]="form.instrumentId"><option [ngValue]="1">Brent Crude</option><option [ngValue]="2">Gold 1KG</option></select></label>
          <label>Participant ID<input [(ngModel)]="form.participantId"></label>
          <div class="two"><label>Side<select [(ngModel)]="form.side"><option>BUY</option><option>SELL</option></select></label><label>Type<select [(ngModel)]="form.orderType"><option>LIMIT</option><option>MARKET</option></select></label></div>
          <div class="two"><label>Price<input type="number" step="0.01" [(ngModel)]="form.price"></label><label>Quantity<input type="number" step="0.01" [(ngModel)]="form.quantity"></label></div>
          <button (click)="submit()">Submit test order</button><p class="muted">{{message}}</p>
        </article>
      </section>
      <footer>Demo only — order acknowledgement is not an execution or trade confirmation.</footer>
    </main>`
})
class AppComponent implements OnInit {
  private api = inject(TradingApi);
  instruments: Instrument[] = [];
  message = 'Loading instruments…';
  form = { instrumentId: 1, participantId: 'DEMO-PARTICIPANT', side: 'BUY', orderType: 'LIMIT', price: 75, quantity: 10 };
  async ngOnInit() { try { this.instruments = await this.api.instruments(); this.message = 'Instrument reference data loaded.'; } catch { this.message = 'API unavailable. Start the Spring Boot service.'; } }
  async submit() {
    try { const result = await this.api.submit(this.form); this.message = 'Received: ' + result.orderId + ' (' + result.status + ')'; }
    catch { this.message = 'Order request failed. Check API availability and input.'; }
  }
}
bootstrapApplication(AppComponent, { providers: [provideHttpClient()] }).catch(console.error);
