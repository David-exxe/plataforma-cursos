import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Configurar2faResponse, LoginRequest, LoginResponse, RegistroRequest, Usuario } from '../models';

interface Sesion {
  token: string;
  usuario: Usuario;
  /** Epoch ms. Se calcula con el reloj del navegador (expiraEnSegundos), inmune a desfases con el servidor. */
  expiraEn: number;
}

const CLAVE = 'academia.sesion';

/** Sesión JWT del estudiante/administrador + flujo de login con 2FA (TOTP). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/api/auth`;
  private readonly _sesion = signal<Sesion | null>(this.restaurar());
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  readonly usuario = computed(() => this._sesion()?.usuario ?? null);
  readonly autenticado = computed(() => this._sesion() !== null);
  readonly esAdmin = computed(() => this.usuario()?.rol === 'ADMIN');
  /** true cuando la sesión terminó por expiración o por un 401 (para mostrar un aviso en el login). */
  readonly sesionExpirada = signal(false);

  constructor() {
    this.programarExpiracion();
  }

  /* ---------- flujo de acceso ---------- */
  registrar(req: RegistroRequest) {
    return this.http.post<Usuario>(`${this.api}/registro`, req);
  }

  /** Paso 1. Si el usuario tiene 2FA, responde { requiere2fa: true, tokenTemporal }. */
  login(req: LoginRequest) {
    return this.http.post<LoginResponse>(`${this.api}/login`, req).pipe(tap((r) => this.iniciarSesion(r)));
  }

  /** Paso 2 (solo con 2FA): token temporal (5 min) + código de 6 dígitos. */
  verificar2fa(tokenTemporal: string, codigo: string) {
    return this.http
      .post<LoginResponse>(`${this.api}/2fa/verificar`, { tokenTemporal, codigo })
      .pipe(tap((r) => this.iniciarSesion(r)));
  }

  perfil() {
    return this.http.get<Usuario>(`${this.api}/me`).pipe(tap((u) => this.actualizarUsuario(u)));
  }

  /* ---------- gestión de 2FA ---------- */
  configurar2fa() {
    return this.http.post<Configurar2faResponse>(`${this.api}/2fa/configurar`, {});
  }

  activar2fa(codigo: string) {
    return this.http.post<Usuario>(`${this.api}/2fa/activar`, { codigo }).pipe(tap((u) => this.actualizarUsuario(u)));
  }

  desactivar2fa(codigo: string) {
    return this.http.post<Usuario>(`${this.api}/2fa/desactivar`, { codigo }).pipe(tap((u) => this.actualizarUsuario(u)));
  }

  /* ---------- sesión ---------- */
  /** Token si la sesión sigue vigente; si venció la limpia y devuelve null. */
  tokenVigente(): string | null {
    const s = this._sesion();
    if (!s) return null;
    if (Date.now() >= s.expiraEn - 5_000) {
      this.limpiar();
      this.sesionExpirada.set(true);
      return null;
    }
    return s.token;
  }

  cerrarSesion(porExpiracion = false): void {
    this.limpiar();
    this.sesionExpirada.set(porExpiracion);
  }

  private actualizarUsuario(u: Usuario): void {
    const s = this._sesion();
    if (s) this.guardar({ ...s, usuario: u });
  }

  private iniciarSesion(r: LoginResponse): void {
    if (!r.accessToken || !r.usuario) return; // requiere2fa: todavía no hay sesión
    this.sesionExpirada.set(false);
    this.guardar({ token: r.accessToken, usuario: r.usuario, expiraEn: Date.now() + r.expiraEnSegundos * 1000 });
  }

  private guardar(s: Sesion): void {
    this._sesion.set(s);
    try {
      localStorage.setItem(CLAVE, JSON.stringify(s));
    } catch {
      /* modo privado / sin almacenamiento: la sesión vive solo en memoria */
    }
    this.programarExpiracion();
  }

  private limpiar(): void {
    this._sesion.set(null);
    if (this.temporizador) clearTimeout(this.temporizador);
    this.temporizador = null;
    try {
      localStorage.removeItem(CLAVE);
    } catch {
      /* nada que limpiar */
    }
  }

  private restaurar(): Sesion | null {
    try {
      const crudo = localStorage.getItem(CLAVE);
      if (!crudo) return null;
      const s = JSON.parse(crudo) as Sesion;
      if (!s?.token || !s.usuario || s.expiraEn <= Date.now()) {
        localStorage.removeItem(CLAVE);
        return null;
      }
      return s;
    } catch {
      return null;
    }
  }

  private programarExpiracion(): void {
    if (this.temporizador) clearTimeout(this.temporizador);
    const s = this._sesion();
    if (!s) return;
    const ms = Math.min(Math.max(0, s.expiraEn - Date.now()), 2_000_000_000);
    this.temporizador = setTimeout(() => {
      this.limpiar();
      this.sesionExpirada.set(true);
    }, ms);
  }
}
