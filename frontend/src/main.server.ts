import { BootstrapContext, bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { serverConfig } from './app/app.config.server';

// Used only at build time to prerender static routes; nothing runs on a server in production.
const bootstrap = (context: BootstrapContext) => bootstrapApplication(AppComponent, serverConfig, context);

export default bootstrap;
