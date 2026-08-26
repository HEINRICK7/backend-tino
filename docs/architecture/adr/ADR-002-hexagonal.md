# ADR-002 — Hexagonal Architecture

Status: Accepted

## Context
Regras de negócio devem sobreviver a frameworks, banco e fornecedores.

## Decision
Usar cores domain/application com ports; Spring, web, jOOQ, segurança e integrações são adapters apontando para dentro.

## Why
Regras puras são rápidas de testar e infraestrutura permanece substituível.

## Consequences
Há interfaces/composição explícitas e testes impedem vazamento de adapter.

## Rejected alternatives
Scripts controller-to-database e domínio anotado por framework.

## Reconsider when
Nunca globalmente; simplificação local só preservando a direção das dependências.
