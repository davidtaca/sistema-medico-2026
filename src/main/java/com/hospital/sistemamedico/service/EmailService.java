package com.hospital.sistemamedico.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Servicio encargado del envío de correos electrónicos reales del sistema,
 * usando Gmail SMTP configurado en application.properties. Los errores de
 * envío se capturan y solo se registran en consola: un fallo al enviar un
 * correo nunca debe bloquear el registro del usuario ni el pago, ya que son
 * procesos secundarios (notificación), no la operación principal.
 *
 * Todos los correos llevan el nombre del hospital en el asunto y el pie
 * estándar de correo automático (RN-GLOBAL-006).
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.hospital.nombre:Hospital Sistema Médico}")
    private String nombreHospital;

    /** Teléfono de la clínica que se muestra en el pie de los correos (RN-GLOBAL-006); configurable con app.hospital.telefono. */
    @Value("${app.hospital.telefono:}")
    private String telefonoHospital;

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-GT"));
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Pie estándar de todo correo automático (RN-GLOBAL-006). */
    private String pieDeCorreo() {
        String pie = "Este es un correo automático del Sistema Informático Hospitalario. No responda a este mensaje.";
        if (telefonoHospital != null && !telefonoHospital.isBlank()) {
            pie += " Para consultas, comuníquese al teléfono " + telefonoHospital + ".";
        }
        return pie;
    }

    /** Nombre del hospital tal como aparece en los asuntos: "Hospital [Nombre]". */
    private String hospital() {
        return nombreHospital;
    }

    private String fecha(LocalDateTime f) {
        return FORMATO_FECHA.format(f);
    }

    private String hora(LocalDateTime f) {
        return FORMATO_HORA.format(f);
    }

    /** Envía un correo de texto; devuelve false (sin lanzar error) si el envío falla. */
    private boolean enviar(String destino, String asunto, String texto, String descripcionError) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(destino);
            mensaje.setSubject(asunto);
            mensaje.setText(texto);
            mailSender.send(mensaje);
            return true;
        } catch (Exception e) {
            System.err.println(descripcionError + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Envía el correo de bienvenida a un paciente recién registrado (CU-02, paso final).
     * Asunto: "Bienvenido al Sistema de Citas - Hospital [Nombre]".
     *
     * @param correoDestino correo electrónico del paciente
     * @param nombrePaciente nombre completo del paciente, usado en el saludo
     */
    public void enviarCorreoBienvenida(String correoDestino, String nombrePaciente) {
        enviar(correoDestino, "Bienvenido al Sistema de Citas - " + hospital(),
                "Estimado(a) " + nombrePaciente + ", su registro ha sido completado exitosamente. "
                        + "Ya puede agendar sus citas médicas a través de nuestro portal.\n\n" + pieDeCorreo(),
                "No se pudo enviar el correo de bienvenida");
    }

    /**
     * Confirmación de una cita ya pagada (RN-CU00-02). Asunto: "Confirmación de
     * Cita Médica - Hospital [Nombre]"; incluye paciente, especialidad, médico,
     * fecha, hora y sucursal.
     */
    public void enviarConfirmacionCita(String correoDestino, String nombrePaciente, String especialidad,
                                       String nombreMedico, LocalDateTime fechaHora, String sucursal) {
        enviar(correoDestino, "Confirmación de Cita Médica - " + hospital(),
                "Estimado(a) " + nombrePaciente + ",\n\n"
                        + "Su cita ha sido confirmada:\n\n"
                        + "Paciente: " + nombrePaciente + "\n"
                        + "Especialidad: " + especialidad + "\n"
                        + "Médico: " + nombreMedico + "\n"
                        + "Fecha: " + fecha(fechaHora) + "\n"
                        + "Hora: " + hora(fechaHora) + "\n"
                        + "Sucursal: " + sucursal + "\n\n"
                        + pieDeCorreo(),
                "No se pudo enviar la confirmación de la cita");
    }

    /**
     * Envía el comprobante de pago por correo al paciente tras un pago exitoso
     * (CU-04, RN-CU04-05; RN-GLOBAL-005). Asunto: "Comprobante de Pago - Cita
     * Médica - Hospital [Nombre]".
     *
     * @param correoDestino correo electrónico del paciente
     * @param nombrePaciente nombre completo del paciente
     * @param numeroTransaccion número único de la transacción de pago
     * @param monto monto pagado, ya formateado como texto
     * @param formaPago forma de pago (por ejemplo "Tarjeta" o "Efectivo")
     * @param fechaPago fecha y hora de la transacción
     * @param nombreMedico nombre del médico asignado a la cita
     * @param especialidad nombre de la especialidad de la cita
     * @param sucursal nombre de la sucursal donde se atenderá
     * @param fechaCita fecha y hora de la cita
     */
    public void enviarComprobantePago(String correoDestino, String nombrePaciente, String numeroTransaccion, String monto,
                                      String formaPago, LocalDateTime fechaPago, String nombreMedico, String especialidad,
                                      String sucursal, LocalDateTime fechaCita) {
        enviar(correoDestino, "Comprobante de Pago - Cita Médica - " + hospital(),
                "Estimado(a) " + nombrePaciente + ",\n\n"
                        + "Su pago ha sido procesado exitosamente. Detalle del comprobante:\n\n"
                        + "Número de transacción: " + numeroTransaccion + "\n"
                        + "Paciente: " + nombrePaciente + "\n"
                        + "Monto pagado: Q" + monto + "\n"
                        + "Forma de pago: " + formaPago + "\n"
                        + "Fecha y hora de la transacción: " + fecha(fechaPago) + ", " + hora(fechaPago) + "\n"
                        + "Sucursal: " + sucursal + "\n\n"
                        + "Detalle de la cita:\n"
                        + "Especialidad: " + especialidad + "\n"
                        + "Médico: " + nombreMedico + "\n"
                        + "Fecha: " + fecha(fechaCita) + "\n"
                        + "Hora: " + hora(fechaCita) + "\n\n"
                        + "Gracias por confiar en nuestro hospital.\n\n" + pieDeCorreo(),
                "No se pudo enviar el comprobante de pago");
    }

    /**
     * Notifica al paciente que su médico le agendó una cita de seguimiento
     * (CU-12, RN-CU11-04): fecha, hora, tipo de seguimiento, médico, sucursal
     * y observaciones. Asunto: "Cita de Seguimiento Agendada - Hospital [Nombre]".
     *
     * @return true si el correo se envió
     */
    public boolean enviarNotificacionSeguimiento(String correoDestino, String nombrePaciente, String nombreMedico,
                                                 String sucursal, LocalDateTime fechaHora, String tipoSeguimiento,
                                                 String observaciones) {
        boolean ok = enviar(correoDestino, "Cita de Seguimiento Agendada - " + hospital(),
                "Estimado(a) " + nombrePaciente + ",\n\n"
                        + "Su médico le agendó una cita de seguimiento:\n\n"
                        + "Fecha: " + fecha(fechaHora) + "\n"
                        + "Hora: " + hora(fechaHora) + "\n"
                        + "Tipo de seguimiento: " + tipoSeguimiento + "\n"
                        + "Médico: " + nombreMedico + "\n"
                        + "Sucursal: " + sucursal + "\n"
                        + "Observaciones: " + observaciones + "\n\n"
                        + "Ingrese al portal para completar el pago y confirmar su cita.\n\n"
                        + pieDeCorreo(),
                "Error al enviar notificación por correo electrónico al paciente " + nombrePaciente + ". Se reintentará automáticamente");
        return ok;
    }

    /**
     * Envía el recordatorio de una cita de seguimiento (CU-12, RN-CU11-05).
     *
     * @return true si el correo se envió; false si falló (el recordatorio se reintenta después)
     */
    public boolean enviarRecordatorioSeguimiento(String correoDestino, String nombrePaciente, String nombreMedico,
                                                 String sucursal, LocalDateTime fechaHora, String tipoSeguimiento) {
        return enviar(correoDestino, "Recordatorio: Su Cita de Seguimiento Mañana",
                "Estimado(a) " + nombrePaciente + ",\n\n"
                        + "Le recordamos su próxima cita de seguimiento:\n\n"
                        + "Fecha: " + fecha(fechaHora) + "\n"
                        + "Hora: " + hora(fechaHora) + "\n"
                        + "Tipo de seguimiento: " + tipoSeguimiento + "\n"
                        + "Médico: " + nombreMedico + "\n"
                        + "Sucursal: " + sucursal + "\n\n"
                        + pieDeCorreo(),
                "Error al enviar notificación por correo electrónico al paciente " + nombrePaciente + ". Se reintentará automáticamente");
    }

    /**
     * Notifica al médico tratante que farmacia sustituyó un medicamento de su
     * receta (CU-11, FA02).
     */
    public void enviarNotificacionSustitucion(String correoDestino, String nombreMedico, String paciente, Long recetaId,
                                              String original, String alternativa, String razon) {
        enviar(correoDestino, "Sustitución de medicamento - Receta #" + recetaId + " - " + hospital(),
                "Estimado(a) " + nombreMedico + ",\n\n"
                        + "Farmacia sustituyó un medicamento de la receta #" + recetaId + " del paciente " + paciente + ":\n\n"
                        + "Medicamento recetado: " + original + "\n"
                        + "Medicamento entregado: " + alternativa + "\n"
                        + "Razón: " + razon + "\n\n"
                        + pieDeCorreo(),
                "No se pudo enviar la notificación de sustitución");
    }
}
