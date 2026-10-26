package app.zelar.ocorrencia.storage;

import app.zelar.ocorrencia.dto.FotografiaValidada;

public interface FotografiaStorage {

    void salvar(String chave, FotografiaValidada fotografia);

    byte[] ler(String chave);

    void excluir(String chave);
}
