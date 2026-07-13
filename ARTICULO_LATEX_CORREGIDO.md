# Artículo IEEE — LaTeX corregido

Código LaTeX del artículo con las correcciones de la revisión aplicadas. Compilar con **pdflatex** (recomendado: Overleaf, plantilla vacía + pegar este código).

**Correcciones aplicadas respecto a la versión anterior:**

1. **§ Two-Phase Commit:** la descripción de la fase PREPARE ahora refleja la implementación real (**débito tentativo**: el origen resta el importe ya en PREPARE y lo devuelve en ABORT), en lugar de la fórmula «saldo disponible = saldo − reservado» que no corresponde al código y contradecía al informe.
2. **«versión 13» → «versión N»** en la figura de replicación y en la Tabla I, con comentario `%` recordando reemplazar N por el valor real observado en la captura.
3. Comentario en la Tabla I: si desborda la columna, reducir `p{5.2cm}` a `p{4.9cm}`.

---

```latex
% =========================================================================
% Artículo — Síntesis del desarrollo del proyecto
% Sistema Bancario Distribuido · Curso: Sistemas Distribuidos · 2026-B
% Formato: IEEE conference, dos columnas. Compilar con pdflatex (u Overleaf).
%
% Convención de figuras pendientes:
%   [captura de ...]  -> reemplazar por una captura de pantalla real del sistema
%   [imagen de ...]   -> prompt para generar la figura con IA y luego insertarla
%
% CORREGIDO: fase PREPARE descrita como débito tentativo (coincide con la
% implementación y con el informe); "versión 13" -> "versión N" (reemplazar
% por el valor real tras la captura).
% =========================================================================
\documentclass[conference]{IEEEtran}

\usepackage[utf8]{inputenc}
\usepackage[T1]{fontenc}
\usepackage[spanish,es-tabla]{babel}
\usepackage{graphicx}
\usepackage{booktabs}
\usepackage{url}
\usepackage{xcolor}

% ---- Placeholders de figuras ----
\newcommand{\phcaptura}[1]{%
  \fbox{\parbox[c][3.2cm][c]{0.93\linewidth}{\centering\color{red!70!black}\bfseries [captura de #1]}}}
\newcommand{\phimagen}[1]{%
  \fbox{\parbox[c][3.2cm][c]{0.93\linewidth}{\centering\color{blue!70!black}\bfseries [imagen de #1]}}}

\begin{document}

\title{Sistema Bancario Distribuido: transacciones interbancarias con
Two-Phase Commit, coordinación descentralizada y replicación de archivos}

\author{
\IEEEauthorblockN{Integrante 1 [Nombre y Apellidos]}
\IEEEauthorblockA{[Universidad]\\ {[correo]}}
\and
\IEEEauthorblockN{Integrante 2 [Nombre y Apellidos]}
\IEEEauthorblockA{[Universidad]\\ {[correo]}}
\and
\IEEEauthorblockN{Integrante 3 [Nombre y Apellidos]}
\IEEEauthorblockA{[Universidad]\\ {[correo]}}
\and
\IEEEauthorblockN{Integrante 4 [Nombre y Apellidos]}
\IEEEauthorblockA{[Universidad]\\ {[correo]}}
\and
\IEEEauthorblockN{Integrante 5 [Nombre y Apellidos]}
\IEEEauthorblockA{[Universidad]\\ {[correo]}}
}

\maketitle

% =========================================================================
\begin{abstract}
Este artículo sintetiza el desarrollo de un sistema bancario distribuido
compuesto por tres bancos autónomos, cada uno con agencias en un único país,
cuyos clientes pueden consultar y operar sus cuentas desde cualquier banco
como si fuera una operación local. Cada nodo persiste sus cuentas en archivos
JSON locales, sin base de datos central. Sobre esa base se implementaron los
conceptos centrales del curso: una vista global de cuentas mediante
comunicación entre pares, transferencias interbancarias atómicas con el
protocolo \emph{Two-Phase Commit} (2PC), elección de coordinador con el
algoritmo \emph{Bully}, exclusión mutua distribuida con \emph{Ricart--Agrawala}
sobre relojes lógicos de Lamport, y replicación de archivos en anillo con
control de versiones, reintentos y restauración ante fallos. El sistema se
completa con un API Gateway con autenticación JWT y control de acceso por
roles, un frontend web y despliegue orquestado con Docker Compose. Las pruebas
realizadas verificaron la atomicidad de las transferencias (commit y abort),
la serialización de operaciones concurrentes sobre cuentas compartidas y la
supervivencia del sistema ante la caída y recuperación de un nodo.
\end{abstract}

\begin{IEEEkeywords}
sistemas distribuidos, two-phase commit, algoritmo bully, ricart--agrawala,
relojes de Lamport, replicación, tolerancia a fallos, microservicios
\end{IEEEkeywords}

% =========================================================================
\section{Introducción}

Un conjunto de bancos asociados opera de forma interconectada: cada banco
tiene agencias en un solo país, pero sus clientes realizan transacciones desde
cualquier país como si fueran locales. El reto técnico es que cada banco es un
nodo autónomo que administra únicamente sus propias cuentas, almacenadas en
archivos locales, y aun así el sistema debe comportarse ante el cliente como
una única entidad coherente.

La consigna impone condiciones de datos que el sistema cumple desde sus
semillas: cada banco tiene al menos tres clientes y cada cliente al menos tres
cuentas; existen clientes exclusivos de cada banco (\texttt{C001},
\texttt{C002}, \texttt{C003}); un cliente con cuentas en dos bancos
(\texttt{C100}, en A y B) y un cliente con cuentas en los tres
(\texttt{C200}). Los usuarios pueden acceder a sus cuentas y operar sobre
ellas ---depósitos, retiros, transferencias y consultas--- desde cualquier
banco.

Los objetivos del proyecto fueron: (i) construir tres nodos bancarios
independientes con persistencia en archivo; (ii) lograr una vista global de
las cuentas de un cliente sin importar a qué nodo se consulte; (iii)
garantizar atomicidad en las transferencias entre bancos; (iv) coordinar el
acceso concurrente a cuentas compartidas sin un servidor central de locks;
(v) replicar los archivos de cada banco en otro nodo y tolerar fallos; y
(vi) asegurar el perímetro del sistema con autenticación y autorización.

% =========================================================================
\section{Arquitectura del sistema}

El sistema se compone de cinco servicios (Fig.~\ref{fig:arquitectura}). Un
mismo código, \texttt{banco-service} (Spring Boot, Java 21), se ejecuta tres
veces con perfiles distintos (\texttt{bancoA}, \texttt{bancoB},
\texttt{bancoC}); cada perfil define la identidad del banco, su prioridad para
la elección de coordinador, su puerto (8081--8083), su archivo de datos y la
lista de \emph{peers}. Un API Gateway (Spring Cloud Gateway, puerto 8080) es
el único punto de entrada del frontend (React~18 + Vite, servido con nginx).

\begin{figure}[t]
\centering
\phimagen{diagrama de arquitectura del sistema con el navegador del usuario
arriba conectado a un frontend React, este a un API Gateway central, y el
gateway conectado a tres nodos Banco A, Banco B y Banco C dibujados como
cajas; entre los tres bancos flechas bidireccionales etiquetadas
``/internal/**'' formando un triángulo, y debajo de cada banco un ícono de
archivo JSON y una carpeta de réplicas; estilo diagrama técnico plano con
fondo blanco, bancos en color azul, gateway en color verde, frontend en color
gris y flechas internas en color naranja, con leyenda}
\caption{Arquitectura general: el frontend habla solo con el gateway; los
bancos se comunican entre sí por la red interna \texttt{/internal/**}.}
\label{fig:arquitectura}
\end{figure}

Dos decisiones de diseño atraviesan todo el sistema. Primero, \textbf{no hay
base de datos central}: cada banco persiste un archivo JSON local con
escritura atómica (archivo temporal + \texttt{ATOMIC\_MOVE}) y bloqueo de
archivo (\texttt{FileLock} de Java NIO), manteniendo una copia en memoria
como fuente de verdad en ejecución. Segundo, los endpoints se dividen en dos
planos: \texttt{/api/**} (públicos, consumidos vía gateway) e
\texttt{/internal/**} (exclusivos de la comunicación banco-a-banco, nunca
enrutados por el gateway y protegidos por un secreto compartido).

La convención de numeración de cuentas (\texttt{A-}, \texttt{B-},
\texttt{C-}) permite determinar el banco dueño de una cuenta sin consultas
adicionales, lo que simplifica el enrutado de operaciones y la designación de
participantes en las transacciones distribuidas.

% =========================================================================
\section{Comunicación entre bancos y vista global de cuentas}

La base de todo el comportamiento distribuido es el cliente
\texttt{BancoRemotoClient}: cada banco conoce a sus dos \emph{peers} por
configuración YAML y les habla por REST con \emph{timeouts} cortos (2\,s).
Sus métodos genéricos (\texttt{postInternal}, \texttt{getInternal}) adjuntan
el secreto compartido y son reutilizados por los protocolos de las secciones
siguientes.

La consulta \texttt{GET /api/clientes/\{id\}/cuentas} devuelve la
\textbf{vista global}: une las cuentas locales con las que devuelven los
peers, cada una marcada con su \texttt{bancoId}. Para evitar la recursión
infinita (un banco preguntando a otro que vuelve a preguntar al primero), los
peers se consultan por un endpoint interno que responde \emph{solo} la parte
local: \texttt{/internal/clientes/\{id\}/cuentas-locales}.

El manejo de errores privilegia la disponibilidad: si un peer no responde, se
registra la advertencia y se devuelve la información de los bancos vivos, de
modo que la caída de un nodo degrada la respuesta pero nunca la convierte en
error. Así, el cliente \texttt{C200} obtiene sus 9 cuentas (3 por banco)
preguntando a cualquiera de los tres nodos, y 6 cuentas si uno está caído.

\begin{figure}[t]
\centering
\phcaptura{la respuesta JSON de GET /a/api/clientes/C200/cuentas mostrando
las 9 cuentas del cliente con sus campos bancoId BANCO\_A, BANCO\_B y
BANCO\_C}
\caption{Vista global: las 9 cuentas de \texttt{C200} en los tres bancos,
obtenidas consultando a un solo nodo.}
\label{fig:vistaglobal}
\end{figure}

% =========================================================================
\section{Transacciones distribuidas: Two-Phase Commit}

Una transferencia entre cuentas de bancos distintos debe restar en un archivo
y sumar en otro, en nodos diferentes, de forma atómica: o ambos cambios se
aplican o ninguno. Para ello se implementó el protocolo \emph{Two-Phase
Commit}~\cite{gray1978,bernstein1987}. El banco que recibe la petición actúa
como \textbf{coordinador}; los bancos de las cuentas origen y destino son
\textbf{participantes} con roles \textsc{débito} y \textsc{crédito}.

En la fase \textsc{prepare}, el participante de origen valida el saldo y
\emph{reserva} el monto mediante un \textbf{débito tentativo}: bajo el lock
local de la cuenta, el importe se descuenta ya del saldo, de modo que ninguna
operación concurrente pueda usar fondos comprometidos entre \textsc{prepare}
y la decisión; persiste el estado \texttt{PREPARED} y vota \textsc{yes}. El
participante de destino solo verifica que la cuenta exista. Si ambos votan
\textsc{yes}, el coordinador decide \textsc{commit}: el destino suma el
importe (el origen ya lo descontó) y ambos persisten \texttt{COMMITTED}. Si
alguno vota \textsc{no} ---o no responde, lo que se interpreta como voto
\textsc{no}--- se decide \textsc{abort}: el origen \emph{devuelve} el importe
reservado y ningún saldo queda alterado. El abort sobre un participante que
no llegó a preparar es una operación nula idempotente. La transacción, su
identificador y sus transiciones de estado quedan registradas en el archivo
de cada banco.

El servicio de transferencias enruta cada petición según la ubicación de las
cuentas: misma instancia (transferencia local atómica del Hito~1), ambas en
otro mismo banco (reenvío al dueño, evitando un 2PC innecesario) o bancos
distintos (2PC).

\begin{figure}[t]
\centering
\phcaptura{los logs del banco coordinador durante una transferencia
interbancaria mostrando la secuencia 2PC COORDINADOR tx=TX-... inicia,
PREPARE con voto YES de BANCO\_A y BANCO\_C, y DECISION=COMMIT; junto a los
dos archivos JSON antes y después con la resta y la suma de 100}
\caption{Traza de un 2PC exitoso: \texttt{A-1200} $\rightarrow$
\texttt{C-3200} por 100, con ambos archivos consistentes.}
\label{fig:2pc}
\end{figure}

% =========================================================================
\section{Coordinación y acuerdo}

\subsection{Elección de coordinador: algoritmo Bully}

Para acordar qué nodo asume el papel de coordinador se implementó el
algoritmo \emph{Bully}~\cite{garcia1982}: gana siempre el nodo vivo de mayor
prioridad (A$=$1, B$=$2, C$=$3, definidas por configuración). El nodo que
inicia envía \textsc{elección} a los de prioridad mayor; si alguno responde,
ese continúa la elección; si nadie responde, el iniciador se proclama y
difunde \textsc{coordinador} a todos. Cada banco ejecuta una elección al
arrancar, y puede forzarse por un endpoint interno para las demostraciones.
En operación normal el coordinador resulta ser el Banco~C; si C cae, una
nueva elección proclama a B; cuando C se reincorpora, su elección inicial
recupera la coordinación automáticamente.

\subsection{Exclusión mutua distribuida: Ricart--Agrawala}

El acceso concurrente a una misma cuenta desde varios bancos se serializa con
el algoritmo de Ricart--Agrawala~\cite{ricart1981} sobre relojes lógicos de
Lamport~\cite{lamport1978}. El lock es \emph{por cuenta}: para entrar a la
sección crítica un nodo envía \textsc{request}(cuenta, \emph{timestamp}) a
todos los peers y espera el \textsc{ok} de todos; un receptor difiere su
respuesta solo si está en sección crítica de esa cuenta o la solicita con
\emph{timestamp} menor (empates resueltos por identificador de banco); al
liberar, responde a los encolados. Un peer caído se cuenta como \textsc{ok}
y la espera tiene un límite de 10\,s, privilegiando la disponibilidad.

El coordinador del 2PC adquiere el lock distribuido de ambas cuentas ---en
orden alfabético, el mismo patrón anti-interbloqueo del lock local--- antes
de la fase \textsc{prepare}, y lo libera siempre tras \textsc{commit} o
\textsc{abort}.

\begin{figure}[t]
\centering
\phimagen{diagrama de secuencia UML del algoritmo Ricart-Agrawala con tres
líneas de vida Banco A, Banco B y Banco C, donde Banco A y Banco B envían
REQUEST simultáneos por la misma cuenta con timestamps 5 y 7, Banco A entra
a la sección crítica por tener timestamp menor mientras la petición de
Banco B queda encolada, y al liberar Banco A envía el OK diferido que
permite entrar a Banco B; fondo blanco, mensajes REQUEST en color naranja,
mensajes OK en color verde, sección crítica sombreada en color celeste,
etiquetas de timestamp visibles}
\caption{Serialización de dos peticiones concurrentes por la misma cuenta:
el \emph{timestamp} de Lamport menor gana; el resto espera el OK diferido.}
\label{fig:ricart}
\end{figure}

% =========================================================================
\section{Sistema de archivos distribuidos y tolerancia a fallos}

El archivo de cuentas de cada banco se replica en \textbf{anillo}
(A$\rightarrow$B$\rightarrow$C$\rightarrow$A): tras cada escritura confirmada
---local o distribuida--- se publica un evento interno y el archivo
actualizado se envía asíncronamente al nodo réplica por
\texttt{/internal/replicar}, sin bloquear la operación del usuario. El
receptor guarda la copia en una carpeta separada de sus propios datos.

La consistencia se garantiza con \textbf{control de versiones}: cada estado
del banco lleva un contador monótono y la réplica descarta toda versión
entrante menor o igual a la guardada, de modo que un mensaje tardío nunca
pisa un estado más nuevo. La tolerancia a fallos combina tres mecanismos:
(i) reintentos con \emph{backoff} (3 intentos) al replicar; (ii) una
\textbf{cola de pendientes} que un planificador reprocesa cada 10\,s hasta
que el peer caído regresa; y (iii) \textbf{restauración}: un banco que
perdió su archivo puede reconstruirlo pidiendo a los peers la réplica propia
y adoptando la de versión más alta. En el plano transaccional, la caída de
un participante durante \textsc{prepare} se traduce en voto \textsc{no} y
abort limpio.

% NOTA: reemplazar "versión N" por el número real observado en la captura.
\begin{figure}[t]
\centering
\phcaptura{los logs del Banco B durante el experimento de caída: tres
intentos fallidos de replicación hacia BANCO\_C con Connection refused, el
mensaje Encolando, y tras reencender C el mensaje Replicación de pendiente
exitosa. Versión N enviada a BANCO\_C}
\caption{Tolerancia a fallos en la replicación: la operación principal se
completa, la réplica se encola y se entrega al volver el nodo.}
\label{fig:replica}
\end{figure}

% =========================================================================
\section{Seguridad y punto único de entrada}

Como aporte adicional, el sistema no es abierto. El gateway concentra la
seguridad perimetral: \texttt{POST /auth/login} valida que el cliente exista
en la red de bancos y emite un \textbf{JWT firmado} (HS256) con su identidad
y rol; un filtro global exige el token en toda ruta \texttt{/api/**}
(sin token $\rightarrow$ \texttt{401}) y aplica \textbf{RBAC}: un cliente
solo accede a sus propias cuentas (token de \texttt{C200} sobre datos de
\texttt{C001} $\rightarrow$ \texttt{403}), mientras el rol \texttt{ADMIN}
tiene vista total. La identidad validada se propaga a los bancos en
cabeceras para auditoría.

El plano interno tiene defensa en profundidad: el gateway no define ruta
alguna hacia \texttt{/internal/**}, y además cada banco exige en esos
endpoints la cabecera \texttt{X-Internal-Token} con el secreto compartido
del grupo; una llamada directa sin el secreto recibe \texttt{403}.

El frontend ofrece inicio de sesión por cliente, la vista unificada de
cuentas en los tres bancos, formularios de depósito, retiro y transferencia
(incluidas las interbancarias vía 2PC, con resultado
\texttt{COMMITTED}/\texttt{ABORTED}), historial de transacciones y un
indicador de salud por banco. Todo el sistema se levanta con
\texttt{docker-compose up --build}: cinco contenedores con
\emph{healthchecks} y dependencias ordenadas.

\begin{figure}[t]
\centering
\phcaptura{el dashboard del frontend con la sesión de C200 iniciada,
mostrando sus 9 cuentas agrupadas por banco y el indicador de bancos vivos}
\caption{Vista unificada del cliente \texttt{C200} en el frontend.}
\label{fig:frontend}
\end{figure}

% =========================================================================
\section{Resultados y evaluación}

El sistema completo se compiló y ejecutó de extremo a extremo (tres bancos,
gateway y frontend). La Tabla~\ref{tab:resultados} resume las verificaciones
funcionales realizadas.

% NOTA: si la tabla desborda la columna, reducir p{5.2cm} a p{4.9cm}.
% NOTA: reemplazar "versión N" por el número real observado en la captura.
\begin{table}[t]
\caption{Verificaciones funcionales del sistema}
\label{tab:resultados}
\centering
\small
\begin{tabular}{p{5.2cm}l}
\toprule
\textbf{Prueba} & \textbf{Resultado} \\
\midrule
Vista global de \texttt{C200} desde cualquier nodo & 9 cuentas \\
Vista global de \texttt{C100} & 6 cuentas \\
Vista global con Banco C caído & 6 cuentas, HTTP 200 \\
2PC \texttt{A-1200}$\rightarrow$\texttt{C-3200} (100) & \texttt{COMMITTED} \\
\quad saldos resultantes en ambos archivos & $5000\!\to\!4900$; $4800\!\to\!4900$ \\
2PC con saldo insuficiente & \texttt{ABORTED}, saldos intactos \\
2PC hacia cuenta inexistente & \texttt{ABORTED}, reserva liberada \\
6 transferencias 2PC concurrentes sobre \texttt{B-2200} & 6 \texttt{COMMITTED}, saldo exacto \\
\quad saldo final esperado/observado & $3900$ / $3900$ \\
Elección con los 3 nodos vivos & coordinador $=$ C \\
Elección forzada con C caído & coordinador $=$ B \\
Depósito en B con su réplica (C) caída & exitoso; réplica encolada \\
Reintento de réplica al volver C & entregada (versión N) \\
Acceso sin token JWT & \texttt{401} \\
RBAC: token de C200 sobre C001 & \texttt{403} \\
\texttt{/internal/**} directo sin secreto & \texttt{403} \\
\bottomrule
\end{tabular}
\end{table}

Tres resultados merecen destacarse. Primero, la \textbf{atomicidad}: en el
caso de abort por saldo insuficiente o cuenta inexistente, ningún archivo
sufrió cambios y la transacción quedó registrada como \texttt{ABORTED} en
ambos nodos. Segundo, la \textbf{serialización}: las seis transferencias
concurrentes lanzadas en paralelo desde la cuenta compartida \texttt{B-2200}
terminaron todas en \texttt{COMMITTED} con saldo final exacto
($4200 - 6\times50 = 3900$), sin operaciones perdidas ni duplicadas, gracias
a la exclusión mutua distribuida. Tercero, la \textbf{supervivencia}: con un
nodo caído el sistema siguió atendiendo consultas y operaciones; la
replicación pendiente se entregó automáticamente al reincorporarse el nodo,
y la elección de coordinador convergió en ambas direcciones (caída y
recuperación).

\begin{figure}[t]
\centering
\phcaptura{la terminal con las 6 transferencias concurrentes lanzadas en
paralelo y sus 6 respuestas COMMITTED, seguida de la consulta de saldos
finales B-2200=3900, A-1200=5050 y C-3200=5050}
\caption{Prueba de concurrencia: seis 2PC simultáneos sobre la misma cuenta
compartida, serializados con saldos exactos.}
\label{fig:concurrencia}
\end{figure}

% =========================================================================
\section{Conclusiones y trabajo futuro}

El proyecto demuestra que, sobre nodos autónomos con persistencia en archivos
locales y sin ninguna base de datos central, es posible construir un servicio
bancario coherente aplicando los algoritmos clásicos de la literatura: 2PC
para la atomicidad interbancaria, Bully para el acuerdo de coordinador,
Ricart--Agrawala con relojes de Lamport para la exclusión mutua, y
replicación versionada con reintentos para la disponibilidad de los datos.
La combinación de estos mecanismos ---locks distribuidos protegiendo al 2PC,
replicación disparada tras cada commit, elección reaccionando a las
caídas--- resultó más ilustrativa que cada pieza por separado, porque las
garantías de una dependen de las otras.

Como trabajo futuro se identifican: la recuperación del coordinador 2PC
tras una caída en plena fase de decisión (registro persistente de
transacciones dudosas y protocolo de terminación); heartbeats periódicos
para disparar elecciones sin intervención manual; una suite de pruebas de
integración automatizadas con métricas de latencia y \emph{throughput} bajo
carga; y el cifrado TLS de la comunicación entre nodos.

% =========================================================================
\begin{thebibliography}{9}

\bibitem{lamport1978}
L.~Lamport, ``Time, clocks, and the ordering of events in a distributed
system,'' \emph{Communications of the ACM}, vol.~21, no.~7, pp. 558--565,
1978.

\bibitem{ricart1981}
G.~Ricart y A.~K. Agrawala, ``An optimal algorithm for mutual exclusion in
computer networks,'' \emph{Communications of the ACM}, vol.~24, no.~1,
pp. 9--17, 1981.

\bibitem{garcia1982}
H.~Garcia-Molina, ``Elections in a distributed computing system,''
\emph{IEEE Transactions on Computers}, vol. C-31, no.~1, pp. 48--59, 1982.

\bibitem{gray1978}
J.~Gray, ``Notes on data base operating systems,'' en \emph{Operating
Systems: An Advanced Course}, Lecture Notes in Computer Science, vol.~60,
Springer, 1978, pp. 393--481.

\bibitem{bernstein1987}
P.~A. Bernstein, V.~Hadzilacos y N.~Goodman, \emph{Concurrency Control and
Recovery in Database Systems}. Addison-Wesley, 1987.

\bibitem{coulouris2012}
G.~Coulouris, J.~Dollimore, T.~Kindberg y G.~Blair, \emph{Distributed
Systems: Concepts and Design}, 5.\textsuperscript{a}~ed. Addison-Wesley,
2012.

\bibitem{tanenbaum2017}
A.~S. Tanenbaum y M.~van Steen, \emph{Distributed Systems},
3.\textsuperscript{a}~ed. distributed-systems.net, 2017.

\end{thebibliography}

\end{document}
```
