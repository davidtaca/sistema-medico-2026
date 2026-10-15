package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Especialidad;
import com.hospital.sistemamedico.model.Rol;
import com.hospital.sistemamedico.model.Sucursal;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas de las reglas de creación y edición de usuarios internos (CU-01):
 * sucursal obligatoria para cualquier rol, especialidad del médico, estado
 * al crear y al editar. No necesitan base de datos: el repositorio es simulado.
 */
class UsuarioServiceTest {

    private UsuarioService servicio;
    private UsuarioRepository repositorio;
    private Sucursal sucursal;
    private Especialidad especialidad;

    @BeforeEach
    void preparar() {
        servicio = new UsuarioService();
        repositorio = mock(UsuarioRepository.class);
        ReflectionTestUtils.setField(servicio, "usuarioRepository", repositorio);
        when(repositorio.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        sucursal = new Sucursal();
        sucursal.setId(1L);
        especialidad = new Especialidad();
        especialidad.setId(1L);
    }

    private Usuario interno(Rol rol) {
        Usuario u = new Usuario();
        u.setNombreCompleto("Jocelin Sazo Perez");
        u.setCorreo("jocelin" + rol.name().toLowerCase() + "@example.test");
        u.setUsername("Sazojoce");
        u.setPassword("ClaveSegura123");
        u.setRol(rol);
        u.setSucursal(sucursal);
        if (rol == Rol.MEDICO) {
            u.setEspecialidad(especialidad);
        }
        return u;
    }

    private String error(Usuario u) {
        return assertThrows(IllegalArgumentException.class, () -> servicio.registrarUsuario(u)).getMessage();
    }

    @Test
    void laSucursalEsObligatoriaParaTodoUsuarioInternoSinImportarElRol() {
        for (Rol rol : Rol.values()) {
            if (rol == Rol.PACIENTE) {
                continue;
            }
            Usuario u = interno(rol);
            u.setSucursal(null);
            assertEquals("Debe seleccionar una sucursal para el usuario.", error(u), rol.name());
        }
    }

    @Test
    void cualquierRolInternoConSucursalSeCreaCorrectamente() {
        for (Rol rol : Rol.values()) {
            if (rol == Rol.PACIENTE) {
                continue;
            }
            Usuario creado = servicio.registrarUsuario(interno(rol));
            assertEquals(rol, creado.getRol());
        }
    }

    @Test
    void elRolEsObligatorio() {
        Usuario u = interno(Rol.CAJERO);
        u.setRol(null);
        assertEquals("Debe seleccionar un rol para el usuario.", error(u));
    }

    @Test
    void elMedicoNecesitaEspecialidadPeroLosDemasRolesNo() {
        Usuario medico = interno(Rol.MEDICO);
        medico.setEspecialidad(null);
        assertEquals("Debe seleccionar una especialidad para el médico.", error(medico));
        assertDoesNotThrow(() -> servicio.registrarUsuario(interno(Rol.LABORATORISTA)));
    }

    @Test
    void elEstadoSeEligeAlCrearUnUsuarioInterno() {
        Usuario inactivo = interno(Rol.RECEPCIONISTA);
        inactivo.setActivo(false);
        assertFalse(servicio.registrarUsuario(inactivo).isActivo());

        // por defecto queda activo
        assertTrue(servicio.registrarUsuario(interno(Rol.FARMACEUTICO)).isActivo());
    }

    @Test
    void lasValidacionesDeCamposSiguenAplicandoseAlCrear() {
        Usuario u = interno(Rol.CAJERO);
        u.setUsername("corto");
        assertEquals("El usuario debe contener al menos 8 caracteres.", error(u));

        Usuario p = interno(Rol.CAJERO);
        p.setPassword("corta");
        assertEquals("La contraseña debe contener al menos 12 caracteres.", error(p));

        Usuario t = interno(Rol.CAJERO);
        t.setTelefono("123");
        assertEquals("El número de teléfono debe contener exactamente 8 dígitos numéricos.", error(t));

        Usuario s = interno(Rol.CAJERO);
        s.setNumeroSeguro("abc");
        assertEquals("El número de seguro debe contener entre 5 y 50 caracteres.", error(s));
    }

    // ------------------------------------------------------------ edición

    private Usuario existente(Rol rol) {
        Usuario u = interno(rol);
        u.setId(5L);
        when(repositorio.findById(5L)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void alEditarElEstadoSoloCambiaSiSeIndica() {
        Usuario u = existente(Rol.CAJERO);

        servicio.actualizarUsuario(5L, new Usuario(), false);
        assertFalse(u.isActivo());

        servicio.actualizarUsuario(5L, new Usuario(), true);
        assertTrue(u.isActivo());

        // sin indicar el estado no se modifica, aunque el objeto de datos traiga "activo" por defecto
        u.setActivo(false);
        servicio.actualizarUsuario(5L, new Usuario());
        assertFalse(u.isActivo());
    }

    @Test
    void alEditarLaSucursalEsOpcionalYSePuedeAsignarAUnRolNoMedico() {
        Usuario u = existente(Rol.ENFERMERO);
        u.setSucursal(null);
        assertDoesNotThrow(() -> servicio.actualizarUsuario(5L, new Usuario(), null));

        Sucursal nueva = new Sucursal();
        nueva.setId(2L);
        Usuario cambios = new Usuario();
        cambios.setSucursal(nueva);
        servicio.actualizarUsuario(5L, cambios, null);
        assertEquals(2L, u.getSucursal().getId());
    }

    @Test
    void alEditarUnMedicoNoPuedeQuedarSinEspecialidad() {
        Usuario u = existente(Rol.CAJERO);
        Usuario cambios = new Usuario();
        cambios.setRol(Rol.MEDICO);
        assertEquals("Debe seleccionar una especialidad para el médico.",
                assertThrows(IllegalArgumentException.class, () -> servicio.actualizarUsuario(5L, cambios, null)).getMessage());

        cambios.setEspecialidad(especialidad);
        assertEquals(Rol.MEDICO, servicio.actualizarUsuario(5L, cambios, null).getRol());
        assertNotNull(u.getEspecialidad());
    }

    // ------------------------------------------------------------ contraseñas y DPI

    @Test
    void laContrasenaSeGuardaCifradaYNuncaEnTextoPlano() {
        Usuario creado = servicio.registrarUsuario(interno(Rol.CAJERO));
        assertTrue(creado.getPassword().startsWith("$2"), creado.getPassword());
        assertNotEquals("ClaveSegura123", creado.getPassword());
    }

    @Test
    void elInicioDeSesionFuncionaConLaContrasenaCifrada() {
        Usuario creado = servicio.registrarUsuario(interno(Rol.CAJERO));
        when(repositorio.findByUsername("Sazojoce")).thenReturn(Optional.of(creado));

        assertEquals(creado, servicio.autenticar("Sazojoce", "ClaveSegura123"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> servicio.autenticar("Sazojoce", "otraclave123")).getMessage()
                .startsWith("Usuario o contraseña incorrectos. Intentos restantes:"));
    }

    @Test
    void unaCuentaAntiguaConContrasenaEnTextoPlanoSigueEntrandoYSeCifra() {
        Usuario antiguo = interno(Rol.CAJERO);
        antiguo.setPassword("ClaveAntigua123");
        when(repositorio.findByUsername("Sazojoce")).thenReturn(Optional.of(antiguo));

        assertEquals(antiguo, servicio.autenticar("Sazojoce", "ClaveAntigua123"));
        assertTrue(antiguo.getPassword().startsWith("$2"));
        // ya cifrada, sigue funcionando con la misma contraseña
        assertEquals(antiguo, servicio.autenticar("Sazojoce", "ClaveAntigua123"));
    }

    @Test
    void alMigrarSoloSeCifranLasContrasenasEnTextoPlano() {
        Usuario plano = interno(Rol.CAJERO);
        plano.setPassword("ClavePlana1234");
        Usuario ya = servicio.registrarUsuario(interno(Rol.ENFERMERO));
        String hashOriginal = ya.getPassword();
        when(repositorio.findAll()).thenReturn(java.util.List.of(plano, ya));

        assertEquals(1, servicio.cifrarContrasenasEnTextoPlano());
        assertTrue(plano.getPassword().startsWith("$2"));
        assertEquals(hashOriginal, ya.getPassword());
    }

    @Test
    void alEditarLaNuevaContrasenaTambienSeCifra() {
        Usuario u = existente(Rol.CAJERO);
        Usuario cambios = new Usuario();
        cambios.setPassword("NuevaClave12345");
        servicio.actualizarUsuario(5L, cambios, null);
        assertTrue(u.getPassword().startsWith("$2"));
    }

    @Test
    void elDpiUsaLosTresMensajesDeLaReglaGlobal() {
        Usuario u = interno(Rol.CAJERO);
        u.setDpi("12345678901A3");
        assertEquals("El DPI debe contener únicamente números. No se permiten letras ni caracteres especiales.", error(u));
        u.setDpi("123456789012");
        assertEquals("El DPI debe contener exactamente 13 dígitos. Usted ingresó 12 dígitos.", error(u));

        Usuario paciente = interno(Rol.PACIENTE);
        paciente.setSucursal(null);
        paciente.setDpi(null);
        paciente.setTelefono("12345678");
        paciente.setNit("12345678");
        assertEquals("El campo DPI es obligatorio. Por favor, ingrese su número de DPI.", error(paciente));
    }

    @Test
    void laBusquedaPorDpiValidaElFormato() {
        assertEquals("El DPI debe contener exactamente 13 dígitos. Usted ingresó 5 dígitos.",
                assertThrows(IllegalArgumentException.class, () -> servicio.buscarPorDpi("12345")).getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> servicio.buscarPorDpi("abc")).getMessage()
                .startsWith("El DPI debe contener únicamente números"));
        assertEquals("El campo DPI es obligatorio. Por favor, ingrese su número de DPI.",
                assertThrows(IllegalArgumentException.class, () -> servicio.buscarPorDpi("")).getMessage());
    }
}
