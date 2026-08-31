FROM golang:1.22-alpine AS build

WORKDIR /src
COPY go.mod *.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/tino-delivery .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/tino-delivery /tino-delivery
EXPOSE 8080
ENTRYPOINT ["/tino-delivery"]
