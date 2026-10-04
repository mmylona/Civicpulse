import { Routes } from '@angular/router';
import { signedOutGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'map' },
  {
    path: 'map',
    title: 'pages.map.title',
    loadComponent: () => import('./features/map/map-page').then((m) => m.MapPage),
  },
  {
    path: 'login',
    title: 'pages.login.title',
    canActivate: [signedOutGuard],
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'pages.register.title',
    canActivate: [signedOutGuard],
    loadComponent: () => import('./features/auth/register-page').then((m) => m.RegisterPage),
  },
  {
    path: 'forbidden',
    title: 'pages.forbidden.title',
    loadComponent: () => import('./features/forbidden/forbidden-page').then((m) => m.ForbiddenPage),
  },
  {
    path: '**',
    title: 'pages.notFound.title',
    loadComponent: () => import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
  },
];
