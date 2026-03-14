#!/bin/bash
# chmod +x scripts/setup-ec2.sh
set -e

echo "======================================"
echo "Portfolio Backend - EC2 Setup"
echo "======================================"
echo ""

# Update system
echo "Updating system packages..."
sudo dnf update -y

# Install Docker
echo "Installing Docker..."
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker $USER

# Install Docker Compose plugin
echo "Installing Docker Compose..."
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Configure swap space (critical for instances with ≤2GB RAM)
echo "Configuring swap space..."
if [ ! -f /swapfile ]; then
  sudo dd if=/dev/zero of=/swapfile bs=128M count=8
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
  echo "Swap configured: 1GB"
else
  echo "Swap already configured"
fi

# Setup directory structure
echo "Setting up project directories..."
mkdir -p ~/portfolio-backend/backups

# Clone repo
echo "Cloning repository..."
git clone https://github.com/themancalledzac/edens.zac.backend.git ~/portfolio-backend/repo

# Create .env from example
cp ~/portfolio-backend/repo/.env.example ~/portfolio-backend/.env

echo ""
echo "======================================"
echo "Setup complete!"
echo "======================================"
echo ""
echo "Next steps:"
echo "  1. Edit ~/portfolio-backend/.env with your credentials"
echo "     nano ~/portfolio-backend/.env"
echo ""
echo "  2. Log out and back in (so Docker group takes effect)"
echo "     exit"
echo ""
echo "  3. Run the deploy script:"
echo "     bash ~/portfolio-backend/repo/deploy.sh"
echo ""
echo "  4. (Optional) Set up automatic database backups:"
echo "     crontab -e"
echo "     Add: 0 3 * * * bash ~/portfolio-backend/repo/scripts/backup-postgres.sh"
echo ""
