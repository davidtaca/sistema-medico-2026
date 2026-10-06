package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Receta médica emitida por el médico (CU-08, FA04). Contiene uno o más
 * medicamentos (RecetaItem). Queda disponible para farmacia (CU-10), que no
 * la acepta si tiene más de 7 días de antigüedad (RN-CU10-01).
 */
@Entity
@Table(name = "recetas")
public class Receta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cita_id", nullable = false)
    private Cita cita;

    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<RecetaItem> items = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    /**
     * true mientras la receta puede despacharse. Pasa a false cuando farmacia
     * entrega los medicamentos (CU-11), para que no se despache dos veces.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activa = true;

    public Receta() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public List<RecetaItem> getItems() { return items; }
    public void setItems(List<RecetaItem> items) { this.items = items; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
