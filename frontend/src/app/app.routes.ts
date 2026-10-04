import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'map' },
  {
    path: 'map',
    title: 'pages.map.title',
    loadComponent: () => import('./features/map/map-page').then((m) => m.MapPage),
  },
  {
    path: '**',
    title: 'pages.notFound.title',
    loadComponent: () => import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
  },
];
