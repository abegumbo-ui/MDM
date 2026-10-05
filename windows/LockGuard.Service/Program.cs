using LockGuard.Service;

var builder = Host.CreateApplicationBuilder(args);
builder.Services.AddWindowsService(options => options.ServiceName = "LockGuard");
builder.Services.AddHostedService<Worker>();

var host = builder.Build();
host.Run();
