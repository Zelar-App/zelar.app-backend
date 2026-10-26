package app.zelar.ocorrencia.entity;

import app.zelar.ocorrencia.exception.TransicaoStatusInvalidaException;
import app.zelar.usuario.entity.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ocorrencia")
public class Ocorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CategoriaOcorrencia categoria;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Embedded
    private Localizacao localizacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatusOcorrencia status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PrioridadeOcorrencia prioridade;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    protected Ocorrencia() {
    }

    public Ocorrencia(Usuario usuario, CategoriaOcorrencia categoria,
                      String descricao, Localizacao localizacao) {
        this.usuario = Objects.requireNonNull(usuario, "O usuário é obrigatório.");
        this.categoria = Objects.requireNonNull(categoria, "A categoria é obrigatória.");
        Assert.hasText(descricao, "A descrição é obrigatória.");
        this.descricao = descricao;
        this.localizacao = Objects.requireNonNull(localizacao, "A localização é obrigatória.");
        this.status = StatusOcorrencia.ENVIADA;
        this.dataCriacao = LocalDateTime.now();
    }

    public void atualizarStatus(StatusOcorrencia novoStatus) {
        Objects.requireNonNull(novoStatus, "O status é obrigatório.");
        if (!status.permiteTransicaoPara(novoStatus)) {
            throw new TransicaoStatusInvalidaException();
        }
        this.status = novoStatus;
        this.dataAtualizacao = LocalDateTime.now();
    }

    public void atualizarPrioridade(PrioridadeOcorrencia novaPrioridade) {
        Objects.requireNonNull(novaPrioridade, "A prioridade é obrigatória.");
        if (this.prioridade == novaPrioridade) {
            return;
        }
        this.prioridade = novaPrioridade;
        this.dataAtualizacao = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public CategoriaOcorrencia getCategoria() {
        return categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public Localizacao getLocalizacao() {
        return localizacao;
    }

    public StatusOcorrencia getStatus() {
        return status;
    }

    public PrioridadeOcorrencia getPrioridade() {
        return prioridade;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public LocalDateTime getDataAtualizacao() {
        return dataAtualizacao;
    }
}
