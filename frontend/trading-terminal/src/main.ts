import { Component, Injectable, OnInit, inject } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { HttpClient, HttpHeaders, provideHttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

interface Instrument { id: string; symbol: string; name: string; commodity_type: string; currency: string; unit: string; tick_size: number; }
interface BookLevel { side: string; price: number; quantity: number; order_count: number; }
interface LoginResult { accessToken: string; refreshToken: string; user: { email: string; roles: string[]; participantId: string }; }

@Injectable({ providedIn: 'root' })
class TradingApi {
  private http = inject(HttpClient);
  private base = '/api/v1';
  private headers() { return new HttpHeaders({ Authorization: 'Bearer ' + (sessionStorage.getItem('commoditycx_access') || '') }); }
  login(email: string, password: string, mfaCode?: string) {
    return firstValueFrom(this.http.post<LoginResult>(this.base + '/auth/login', { email, password, mfaCode }));
  }
  logout() { return firstValueFrom(this.http.post(this.base + '/auth/logout', { refreshToken: sessionStorage.getItem('commoditycx_refresh') }, { headers: this.headers() })); }
  instruments() { return firstValueFrom(this.http.get<Instrument[]>(this.base + '/instruments', { headers: this.headers() })); }
  book(id: string) { return firstValueFrom(this.http.get<BookLevel[]>(this.base + '/instruments/' + id + '/order-book', { headers: this.headers() })); }
  order(payload: object) {
    return firstValueFrom(this.http.post<unknown[]>(this.base + '/orders', payload, {
      headers: this.headers().set('Idempotency-Key', crypto.randomUUID())
    }));
  }
  rfq(payload: object) { return firstValueFrom(this.http.post(this.base + '/rfqs', payload, { headers: this.headers() })); }
  quote(id: string, payload: object) { return firstValueFrom(this.http.post(this.base + '/rfqs/' + id + '/quotes', payload, { headers: this.headers() })); }
  negotiation(id: string, payload: object) { return firstValueFrom(this.http.post(this.base + '/rfqs/' + id + '/negotiations', payload, { headers: this.headers() })); }
}

@Component({
 selector: 'commoditycx-app', standalone: true, imports: [FormsModule],
 template: `
 <main>
  <header><div><p class="eyebrow">MULTI-MARKET COMMODITIES PLATFORM</p><h1>Commodity<span>CX</span></h1></div><div class="session">@if (loggedIn) { <span>{{email}} · {{roles.join(', ')}}</span><button class="quiet" (click)="logout()">Sign out</button> } @else { <span>SECURE SIGN IN</span> }</div></header>
  @if (!loggedIn) {
   <section class="login panel"><p class="eyebrow">PARTICIPANT ACCESS</p><h2>Sign in to the trading terminal</h2>
    <label>Email<input type="email" [(ngModel)]="loginForm.email" autocomplete="username"></label>
    <label>Password<input type="password" [(ngModel)]="loginForm.password" autocomplete="current-password"></label>
    <label>MFA code (if enabled)<input inputmode="numeric" maxlength="6" [(ngModel)]="loginForm.mfaCode"></label>
    <button (click)="login()">Sign in</button><p class="message">{{message}}</p>
   </section>
  } @else {
   <section class="intro"><div><p class="eyebrow">TRADING WORKSPACE</p><h2>Markets, orders & bilateral flow</h2><p class="muted">Authenticated participant workspace · development environment</p></div><span class="environment">CONNECTED</span></section>
   <section class="grid">
    <article class="panel"><p class="eyebrow">REFERENCE DATA</p><h3>Available instruments</h3>
     @for (item of instruments; track item.id) { <button class="instrument" [class.selected]="form.instrumentId===item.id" (click)="selectInstrument(item.id)"><span><strong>{{item.name}}</strong><small>{{item.symbol}} · {{item.commodity_type}}</small></span><span>{{item.currency}} / {{item.unit}}</span></button> }
     @if (!instruments.length) { <p class="muted">No active instruments returned by the API.</p> }
    </article>
    <article class="panel"><p class="eyebrow">ORDER ENTRY</p><h3>Place an order</h3>
     <label>Instrument<select [(ngModel)]="form.instrumentId"><option value="">Select instrument</option>@for(item of instruments;track item.id){<option [value]="item.id">{{item.symbol}} — {{item.name}}</option>}</select></label>
     <div class="two"><label>Side<select [(ngModel)]="form.side"><option>BUY</option></select></label><label>Type<select [(ngModel)]="form.orderType"><option>LIMIT</option><option>MARKET</option></select></label></div>
     <div class="two"><label>Limit price<input type="number" min="0" step="0.01" [(ngModel)]="form.price"></label><label>Quantity<input type="number" min="0" step="0.01" [(ngModel)]="form.quantity"></label></div>
     <label>Time in force<select [(ngModel)]="form.timeInForce"><option>GTC</option><option>IOC</option></select></label>
     <button (click)="submitOrder()">Submit order</button>
    </article>
    <article class="panel"><p class="eyebrow">CENTRAL ORDER BOOK</p><h3>Market depth</h3><button class="quiet" (click)="loadBook()">Refresh book</button>
     <div class="book-head"><span>Side</span><span>Price</span><span>Quantity</span><span>Orders</span></div>
     @for(level of book;track $index){<div class="book-row"><span [class.buy]="level.side==='BUY'" [class.sell]="level.side==='SELL'">{{level.side}}</span><strong>{{level.price}}</strong><span>{{level.quantity}}</span><span>{{level.order_count}}</span></div>}
     @if(!book.length){<p class="muted">No resting limit orders for this instrument.</p>}
    </article>
    <article class="panel"><p class="eyebrow">OTC RFQ</p><h3>Create request for quote</h3>
     <label>Side<select [(ngModel)]="rfqForm.side"><option>BUY</option></select></label>
     <div class="two"><label>Quantity<input type="number" min="0" [(ngModel)]="rfqForm.quantity"></label><label>Currency<input maxlength="3" [(ngModel)]="rfqForm.currency"></label></div>
     <label>Expires at (ISO timestamp)<input [(ngModel)]="rfqForm.expiresAt"></label>
     <button (click)="createRfq()">Create RFQ</button>
     <label>RFQ ID for quote / negotiation<input [(ngModel)]="rfqId"></label>
     <div class="two"><label>Quote price<input type="number" [(ngModel)]="quoteForm.price"></label><label>Quote quantity<input type="number" [(ngModel)]="quoteForm.quantity"></label></div>
     <button class="secondary" (click)="submitQuote()">Submit seller quote</button>
     <div class="two"><label>Counter price<input type="number" [(ngModel)]="quoteForm.price"></label><label>Counter quantity<input type="number" [(ngModel)]="quoteForm.quantity"></label></div>
     <div class="two"><button class="secondary" (click)="sendCounter()">Send counter-offer</button><button class="secondary" (click)="acceptCounter()">Accept latest offer</button></div>
    </article>
   </section>
   <section class="panel status"><p class="eyebrow">WORKFLOW RESPONSE</p><p class="message">{{message}}</p><pre>{{responseText}}</pre></section>
  }
  <footer>Development environment only. Do not submit live-market orders or real customer data.</footer>
 </main>`
})
class AppComponent implements OnInit {
 private api = inject(TradingApi);
 loggedIn = !!sessionStorage.getItem('commoditycx_access');
 email = sessionStorage.getItem('commoditycx_email') || '';
 roles: string[] = [];
 message = 'Sign in with an approved participant account.';
 responseText = '';
 instruments: Instrument[] = [];
 book: BookLevel[] = [];
 loginForm = { email: '', password: '', mfaCode: '' };
 form = { instrumentId: '', side: 'BUY', orderType: 'LIMIT', price: 75, quantity: 10, timeInForce: 'GTC' };
 rfqForm = { side: 'BUY', quantity: 10, currency: 'USD', expiresAt: new Date(Date.now()+3600000).toISOString() };
 quoteForm = { price: 75, quantity: 10 };
 rfqId = '';
 async ngOnInit() { if (this.loggedIn) await this.loadData(); }
 async login() {
  try { const result = await this.api.login(this.loginForm.email,this.loginForm.password,this.loginForm.mfaCode || undefined);
   sessionStorage.setItem('commoditycx_access',result.accessToken);sessionStorage.setItem('commoditycx_refresh',result.refreshToken);
   sessionStorage.setItem('commoditycx_email',result.user.email);this.email=result.user.email;this.roles=result.user.roles;this.loggedIn=true;this.message='Authenticated successfully.';await this.loadData();
  } catch { this.message='Sign-in failed. Check credentials, participant approval and MFA code.'; }
 }
 async logout() { try { await this.api.logout(); } catch {} sessionStorage.clear();this.loggedIn=false;this.instruments=[];this.book=[];this.message='Signed out.'; }
 async loadData() { try { this.instruments=await this.api.instruments();if(this.instruments.length){this.form.instrumentId=this.instruments[0].id;this.rfqForm.currency=this.instruments[0].currency;await this.loadBook();} } catch { this.message='Unable to load trading data. Verify API and token validity.'; } }
 async selectInstrument(id: string) { this.form.instrumentId=id;await this.loadBook(); }
 async loadBook() { if(!this.form.instrumentId)return;try{this.book=await this.api.book(this.form.instrumentId);}catch{this.book=[];} }
 async submitOrder() { try { const result=await this.api.order({...this.form,instrumentId:this.form.instrumentId,price:this.form.orderType==='LIMIT'?this.form.price:null,riskPrice:this.form.price,timeInForce:this.form.orderType==='MARKET'?'IOC':this.form.timeInForce});this.responseText=JSON.stringify(result,null,2);this.message='Order accepted for processing. Executions, if any, are listed in the response.';await this.loadBook(); } catch { this.message='Order rejected. Verify risk limits, instrument, tick size and API response.'; } }
 async createRfq() { try { const result=await this.api.rfq({...this.rfqForm,instrumentId:this.form.instrumentId});this.responseText=JSON.stringify(result,null,2);this.rfqId=String((result as {id?:string}).id || '');this.message='RFQ created. Share its ID with eligible counterparties.'; } catch { this.message='RFQ creation failed. Check instrument, currency and expiry timestamp.'; } }
 async submitQuote() { try { const result=await this.api.quote(this.rfqId,this.quoteForm);this.responseText=JSON.stringify(result,null,2);this.message='Quote submitted.'; } catch { this.message='Quote submission failed. Confirm the RFQ is open and your participant is eligible.'; } }
 async sendCounter() { try { const result=await this.api.negotiation(this.rfqId,{type:'COUNTER',...this.quoteForm,currency:this.rfqForm.currency});this.responseText=JSON.stringify(result,null,2);this.message='Counter-offer submitted.'; } catch { this.message='Counter-offer failed. Check RFQ status and participant eligibility.'; } }
 async acceptCounter() { try { const result=await this.api.negotiation(this.rfqId,{type:'ACCEPT'});this.responseText=JSON.stringify(result,null,2);this.message='Offer accepted; trade is pending bilateral confirmation.'; } catch { this.message='No eligible counter-offer could be accepted.'; } }
}
bootstrapApplication(AppComponent,{providers:[provideHttpClient()]}).catch(console.error);
