{ config, stdenv, stdenvNoCC, callPackage, mkShell,
  xcodeWrapper, mkFilter, fetchurl, flock, nodejs, bash, zlib, procps,
  status-go, projectNodePackage }:

let
  inherit (stdenv.lib) catAttrs concatStrings unique;

  createMobileFilesSymlinks = root: ''
    # Set up symlinks to mobile enviroment in project root 
    ln -sf ${root}/mobile/js_files/package.json ${root}/package.json
    ln -sf ${root}/mobile/js_files/metro.config.js ${root}/metro.config.js
    ln -sf ${root}/mobile/js_files/yarn.lock ${root}/yarn.lock
  '';

  src =
    let path = ./../../..;
    in builtins.path { # We use builtins.path so that we can name the resulting derivation, otherwise the name would be taken from the checkout directory, which is outside of our control
      inherit path;
      name = "status-react-source-npm-deps";
      filter =
        # Keep this filter as restrictive as possible in order to avoid unnecessary rebuilds and limit closure size
        mkFilter {
          dirRootsToInclude = [ "mobile/js_files" ];
          dirsToExclude = [ ".git" ".svn" "CVS" ".hg" ];
          filesToInclude = [ ".babelrc" ];
          root = path;
        };
    };

  selectedSources = [ status-go ];

  # TARGETS
  shell = mkShell {
    buildInputs = [ xcodeWrapper ];
  };

in {
  inherit xcodeWrapper;

  buildInputs = unique ([
    xcodeWrapper
    flock # used in reset-node_modules.sh
    procps
  ] ++ catAttrs "buildInputs" selectedSources);
  shellHook = ''
    ${status-go.shellHook}

    ${createMobileFilesSymlinks "$STATUS_REACT_HOME"}

    $STATUS_REACT_HOME/nix/mobile/reset-node_modules.sh "${projectNodePackage}" && \
    $STATUS_REACT_HOME/nix/mobile/ios/install-pods-and-status-go.sh || \
    exit
  '';

  # TARGETS
  inherit shell;
}
