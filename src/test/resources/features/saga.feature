# language: pt
Funcionalidade: Saga de finalização da ordem de serviço
  O orquestrador recebe os eventos de orders, stock e billing pelo SQS e publica
  os comandos de cada passo. Os serviços são simulados pelas mensagens que publicariam.

  Cenário: Caminho feliz
    Dado uma ordem com diagnóstico concluído
    Quando o serviço "stock" publica "stockReserved"
    E o serviço "billing" publica "budgetGenerated"
    E o serviço "orders" publica "orderApproved"
    E o serviço "billing" publica "paymentLinkGenerated"
    E o serviço "billing" publica "paymentConfirmed"
    E o serviço "orders" publica "orderFinished"
    E o serviço "stock" publica "stockConsumed"
    Então a saga termina com status "COMPLETED" e sem passo atual
    E os comandos publicados para a ordem foram, em ordem em cada fila:
      | reserveStock        | stock   |
      | generateBudget      | billing |
      | sendApproval        | orders  |
      | generatePaymentLink | billing |
      | sendPaymentLink     | orders  |
      | releaseOrder        | orders  |
      | consumeStock        | stock   |

  Cenário: Pendência de estoque
    Dado uma ordem com diagnóstico concluído
    Quando o serviço "stock" publica "stockPending"
    E o serviço "stock" publica "stockReserved"
    Então a saga está no passo "GENERATE_BUDGET"
    E os comandos publicados para a ordem foram, em ordem em cada fila:
      | reserveStock      | stock   |
      | markStockPending  | orders  |
      | clearStockPending | orders  |
      | generateBudget    | billing |

  Cenário: Orçamento recusado
    Dado uma ordem com diagnóstico concluído
    Quando o serviço "stock" publica "stockReserved"
    E o serviço "billing" publica "budgetGenerated"
    E o serviço "orders" publica "orderRejected"
    Então a saga está no passo "RESTORE_STOCK" com status "COMPENSATING"
    Quando o serviço "stock" publica "stockRestored"
    Então a saga termina com status "COMPENSATED" e sem passo atual
    E os comandos publicados para a ordem foram, em ordem em cada fila:
      | reserveStock   | stock   |
      | generateBudget | billing |
      | sendApproval   | orders  |
      | restoreStock   | stock   |

  Cenário: Pagamento não confirmado
    Dado uma ordem com diagnóstico concluído
    Quando o serviço "stock" publica "stockReserved"
    E o serviço "billing" publica "budgetGenerated"
    E o serviço "orders" publica "orderApproved"
    E o serviço "billing" publica "paymentFailed"
    Então a saga está no passo "CANCEL_ORDER" com status "COMPENSATING"
    Quando o serviço "orders" publica "orderCanceled"
    E o serviço "stock" publica "stockRestored"
    Então a saga termina com status "COMPENSATED" e sem passo atual
    E os comandos publicados para a ordem foram, em ordem em cada fila:
      | reserveStock        | stock   |
      | generateBudget      | billing |
      | sendApproval        | orders  |
      | generatePaymentLink | billing |
      | cancelOrder         | orders  |
      | restoreStock        | stock   |

  Cenário: Pagamento confirmado pelo job do billing sem o link ter sido enviado
    Dado uma ordem com diagnóstico concluído
    Quando o serviço "stock" publica "stockReserved"
    E o serviço "billing" publica "budgetGenerated"
    E o serviço "orders" publica "orderApproved"
    E o serviço "billing" publica "paymentConfirmed"
    E o serviço "orders" publica "orderFinished"
    E o serviço "stock" publica "stockConsumed"
    Então a saga termina com status "COMPLETED" e sem passo atual
    E os comandos publicados para a ordem foram, em ordem em cada fila:
      | reserveStock        | stock   |
      | generateBudget      | billing |
      | sendApproval        | orders  |
      | generatePaymentLink | billing |
      | releaseOrder        | orders  |
      | consumeStock        | stock   |

  Cenário: Evento duplicado é aplicado uma só vez
    Dado uma ordem com diagnóstico concluído
    Quando o serviço "stock" publica "stockReserved"
    E o último evento é entregue novamente
    E o serviço "billing" publica "budgetGenerated"
    Então a saga está no passo "WAITING_APPROVAL"
    E o histórico registra 1 mensagem "stockReserved" com resultado "APPLIED"
    E os comandos publicados para a ordem foram, em ordem em cada fila:
      | reserveStock   | stock   |
      | generateBudget | billing |
      | sendApproval   | orders  |

  Cenário: Diagnóstico repetido não cria uma segunda saga
    Dado uma ordem com diagnóstico concluído
    Quando o serviço "orders" publica "diagnosisCompleted" novamente com outro eventId
    E o serviço "stock" publica "stockReserved"
    Então a saga está no passo "GENERATE_BUDGET"
    E o histórico registra 1 mensagem "diagnosisCompleted" com resultado "DISCARDED_STEP_GUARD"
    E os comandos publicados para a ordem foram, em ordem em cada fila:
      | reserveStock   | stock   |
      | generateBudget | billing |

  Cenário: Mensagem fora de ordem é descartada pela guarda de passo
    Dado uma ordem com diagnóstico concluído
    Quando o serviço "orders" publica "orderApproved"
    E o serviço "stock" publica "stockReserved"
    Então a saga está no passo "GENERATE_BUDGET"
    E o histórico registra 1 mensagem "orderApproved" com resultado "DISCARDED_STEP_GUARD"
    E os comandos publicados para a ordem foram, em ordem em cada fila:
      | reserveStock   | stock   |
      | generateBudget | billing |
