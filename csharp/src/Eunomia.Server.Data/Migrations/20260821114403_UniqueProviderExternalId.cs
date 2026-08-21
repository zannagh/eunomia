using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Eunomia.Server.Data.Migrations
{
    /// <inheritdoc />
    public partial class UniqueProviderExternalId : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_UserExternalLinks_Provider_ExternalId",
                table: "UserExternalLinks");

            migrationBuilder.CreateIndex(
                name: "IX_UserExternalLinks_Provider_ExternalId",
                table: "UserExternalLinks",
                columns: new[] { "Provider", "ExternalId" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_UserExternalLinks_Provider_ExternalId",
                table: "UserExternalLinks");

            migrationBuilder.CreateIndex(
                name: "IX_UserExternalLinks_Provider_ExternalId",
                table: "UserExternalLinks",
                columns: new[] { "Provider", "ExternalId" });
        }
    }
}
