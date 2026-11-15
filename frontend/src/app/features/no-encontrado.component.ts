import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { VacioComponent } from '../shared/vacio.component';

@Component({
  selector: 'app-no-encontrado',
  imports: [RouterLink, VacioComponent],
  template: `
    <section class="container page">
      <app-vacio icono="map-pin" titulo="No encontramos esta página" texto="Puede que el enlace haya cambiado o esté incompleto.">
        <a routerLink="/cursos" class="btn btn-brand">Ver cursos</a>
      </app-vacio>
    </section>
  `,
})
export class NoEncontradoComponent {}
