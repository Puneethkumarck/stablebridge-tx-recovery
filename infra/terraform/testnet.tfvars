# Testnet configuration — use with: terraform apply -var-file=testnet.tfvars
# Set sensitive values via env vars:
#   TF_VAR_evm_ethereum_rpc_url  (Sepolia RPC)
#   TF_VAR_solana_rpc_url        (Solana Devnet RPC)
#   TF_VAR_str_api_key           (API key)
#   TF_VAR_signer_backend        (local-keystore or callback)
#   TF_VAR_signer_keystore_path  (path to keystore JSON)
#   TF_VAR_signer_password       (keystore password)

spring_profiles_active = "testnet"
