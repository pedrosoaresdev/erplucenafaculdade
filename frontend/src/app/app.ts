import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AccessibilityComponent } from './shared/accessibility.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AccessibilityComponent],
  template: `<router-outlet /><app-accessibility />`,
})
export class App {}
