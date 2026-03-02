import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { jwtDecode } from 'jwt-decode';
import { environment } from '../../../environments/environment';

// This service handles authentication, token storage, and user signals.

export interface User {
  id: string;
  email: string;
  role?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenKey = 'access_token';
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  // Signals for reactive state
  user = signal<User | null>(null);
  isAuthenticated = signal<boolean>(false);

  constructor() {
    this.loadStoredUser();
  }

  private loadStoredUser(): void {
    const token = localStorage.getItem(this.tokenKey);
    if (token && !this.isTokenExpired(token)) {
      const decoded: any = jwtDecode(token);
      this.user.set({ id: decoded.sub, email: decoded.email });
      this.isAuthenticated.set(true);
    }
  }

  login(email: string, password: string): Observable<{ token: string }> {
    return this.http.post<{ token: string }>(`${this.baseUrl}/login`, { email, password }).pipe(
      tap(res => this.setSession(res.token))
    );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    this.user.set(null);
    this.isAuthenticated.set(false);
    this.router.navigate(['/login']);
  }

  private setSession(token: string): void {
    localStorage.setItem(this.tokenKey, token);
    const decoded: any = jwtDecode(token);
    this.user.set({ id: decoded.sub, email: decoded.email });
    this.isAuthenticated.set(true);
  }

  private isTokenExpired(token: string): boolean {
    try {
      const decoded: any = jwtDecode(token);
      const expiry = decoded.exp;
      return expiry < Date.now() / 1000;
    } catch {
      return true;
    }
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }
}