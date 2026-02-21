{
  description = "NATS HTTP bridge";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
    clj-nix.url = "github:jlesquembre/clj-nix";
  };

  outputs = { self, nixpkgs, clj-nix }:
    let
      supportedSystems = [ "x86_64-linux" "x86_64-darwin" "aarch64-linux" "aarch64-darwin" ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;
      nixpkgsFor = forAllSystems (system: import nixpkgs { inherit system; overlays = [ self.overlays.default ]; });
    in
    {
      overlays.default = final: prev: {
        nats-http = with final; clj-nix.lib.mkCljApp {
          pkgs = final;
          modules = [
            {
              projectSrc = ./.;
              name = "net.thewagner/nats-http";
              main-ns = "net.thewagner.nats-http.core";
              lockfile = ./deps-lock.json; # Generate it via: nix run .#update-lock
            }
          ];
        };
      };

      packages = forAllSystems (system:
        rec {
          inherit (nixpkgsFor.${system}) nats-http;

          default = nats-http;
        });

      apps = forAllSystems (system: {
        update-lock = {
          type = "app";
          program = "${clj-nix.packages.${system}.deps-lock}/bin/deps-lock";
        };
      });

      nixosModules.nats-http = { config, lib, pkgs, ... }:
        with lib;
        let
          cfg = config.services.nats-http;
        in
        {
          nixpkgs.overlays = [ self.overlays.default ];

          options.services.nats-http = {
            enable = mkEnableOption "NATS to HTTP Bridge Service";
            package = mkOption {
              type = types.package;
              default = pkgs.nats-http;
              description = "The NATS HTTP package to use.";
            };
            natsUrl = mkOption {
              type = types.str;
              default = "nats://nats:4222";
              description = "The URL of the NATS server.";
            };
            configFiles = lib.mkOption {
              type = lib.types.listOf lib.types.path;
              description = "Paths to the configuration file in the Nix store.";
            };
          };

          config = mkIf cfg.enable {
            systemd.services.nats-http = {
              description = "Clojure NATS-HTTP Bridge";
              after = [ "network.target" ];
              wantedBy = [ "multi-user.target" ];

              serviceConfig = {
                ExecStart = "${cfg.package}/bin/nats-http ${lib.concatStringsSep " " cfg.configFiles}";
                Restart = "always";
                DynamicUser = true;
              };

              environment = {
                NATS_URL = cfg.natsUrl;
              };
            };
          };
        };
    };
}
